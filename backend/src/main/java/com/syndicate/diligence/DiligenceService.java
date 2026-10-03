package com.syndicate.diligence;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.common.BadRequestException;
import com.syndicate.common.ConflictException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.diligence.dto.AnswerQuestionRequest;
import com.syndicate.diligence.dto.DiligenceQuestionDto;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.fact.FactRepository;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import com.syndicate.workstream.WorkstreamType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The questions a transaction must answer, held as state rather than as a checklist someone keeps
 * in a spreadsheet. An answer is recorded with the facts and documents it rests on, and accepting
 * an answer is a second person's act.
 */
@Service
@Transactional
public class DiligenceService {

    private final DiligenceQuestionRepository questionRepository;
    private final FactRepository factRepository;
    private final EvidenceRepository evidenceRepository;
    private final TransactionService transactionService;
    private final AuditService auditService;

    public DiligenceService(DiligenceQuestionRepository questionRepository, FactRepository factRepository,
                            EvidenceRepository evidenceRepository, TransactionService transactionService,
                            AuditService auditService) {
        this.questionRepository = questionRepository;
        this.factRepository = factRepository;
        this.evidenceRepository = evidenceRepository;
        this.transactionService = transactionService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DiligenceQuestionDto> list(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return questionRepository.findByTransactionIdOrderByWorkstreamTypeAscCreatedAtAsc(transactionId).stream()
                .map(DiligenceQuestionDto::from)
                .toList();
    }

    public DiligenceQuestionDto answer(UUID questionId, AnswerQuestionRequest request, User caller) {
        DiligenceQuestion question = find(questionId);
        transactionService.requireMembership(question.getTransactionId(), caller.getId());

        if (request.notApplicableReason() != null && !request.notApplicableReason().isBlank()) {
            question.markNotApplicable(request.notApplicableReason(), caller);
            audit(question, caller, "Marked not applicable: " + request.notApplicableReason());
            return DiligenceQuestionDto.from(question);
        }
        if (request.answer() == null || request.answer().isBlank()) {
            throw new BadRequestException("Write the answer, or say why the question does not apply");
        }
        link(question, request);
        if (question.getSupportingFacts().isEmpty() && question.getSupportingEvidence().isEmpty()) {
            throw new BadRequestException("Point at the facts or documents this answer rests on");
        }
        question.answer(request.answer(), caller);
        audit(question, caller, "Answered: " + question.getQuestion());
        return DiligenceQuestionDto.from(question);
    }

    /** Accepting is a second pair of eyes, as with everything else that settles transaction state. */
    public DiligenceQuestionDto accept(UUID questionId, User caller) {
        DiligenceQuestion question = find(questionId);
        transactionService.requireMembership(question.getTransactionId(), caller.getId());
        if (question.getStatus() != DiligenceStatus.ANSWERED) {
            throw new ConflictException("QUESTION_NOT_ANSWERED",
                    "Only an answered question can be accepted (this one is " + question.getStatus() + ")");
        }
        if (question.getAnsweredBy() != null && question.getAnsweredBy().getId().equals(caller.getId())) {
            throw new ConflictException("ANSWER_NOT_INDEPENDENT",
                    "You wrote this answer, so someone else has to accept it");
        }
        question.accept();
        audit(question, caller, "Accepted the answer to: " + question.getQuestion());
        return DiligenceQuestionDto.from(question);
    }

    public DiligenceQuestionDto add(UUID transactionId, String text, WorkstreamType area, IssueSeverity severity,
                                    User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        if (text == null || text.isBlank()) {
            throw new BadRequestException("Write the question");
        }
        DiligenceQuestion question = questionRepository.save(new DiligenceQuestion(transactionId, null, text.trim(),
                area, severity == null ? IssueSeverity.MEDIUM : severity));
        audit(question, caller, "Added question: " + question.getQuestion());
        return DiligenceQuestionDto.from(question);
    }

    private void link(DiligenceQuestion question, AnswerQuestionRequest request) {
        if (request.factIds() != null) {
            for (UUID factId : request.factIds()) {
                question.getSupportingFacts().add(factRepository.findById(factId)
                        .filter(f -> f.getWorkstream().getTransaction().getId().equals(question.getTransactionId()))
                        .orElseThrow(() -> new BadRequestException("Unknown fact " + factId)));
            }
        }
        if (request.evidenceIds() != null) {
            for (UUID evidenceId : request.evidenceIds()) {
                question.getSupportingEvidence().add(evidenceRepository.findById(evidenceId)
                        .filter(e -> e.getWorkstream().getTransaction().getId().equals(question.getTransactionId()))
                        .orElseThrow(() -> new BadRequestException("Unknown document " + evidenceId)));
            }
        }
    }

    private void audit(DiligenceQuestion question, User caller, String summary) {
        auditService.record(question.getTransactionId(), caller, AuditAction.DILIGENCE_QUESTION_UPDATED,
                "DiligenceQuestion", question.getId(), summary, null, question.getStatus().name(), null);
    }

    private DiligenceQuestion find(UUID questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
    }
}
