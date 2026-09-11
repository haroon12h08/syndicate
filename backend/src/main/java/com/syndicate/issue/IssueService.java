package com.syndicate.issue;

import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.issue.dto.CreateIssueRequest;
import com.syndicate.issue.dto.IssueDto;
import com.syndicate.issue.dto.UpdateIssueRequest;
import com.syndicate.user.User;
import com.syndicate.user.UserRepository;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class IssueService {

    private final IssueRepository issueRepository;
    private final WorkstreamService workstreamService;
    private final FactRepository factRepository;
    private final EvidenceRepository evidenceRepository;
    private final UserRepository userRepository;

    public IssueService(IssueRepository issueRepository, WorkstreamService workstreamService,
                         FactRepository factRepository, EvidenceRepository evidenceRepository,
                         UserRepository userRepository) {
        this.issueRepository = issueRepository;
        this.workstreamService = workstreamService;
        this.factRepository = factRepository;
        this.evidenceRepository = evidenceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public IssueDto create(UUID workstreamId, CreateIssueRequest request, User caller) {
        workstreamService.requireAccess(workstreamId, caller.getId());
        Workstream workstream = workstreamService.findWorkstream(workstreamId);
        User owner = request.ownerUserId() != null ? findUser(request.ownerUserId()) : null;
        Issue issue = new Issue(workstream, request.title(), request.description(), request.severity(),
                owner, request.dueDate(), caller);
        if (request.relatedFactIds() != null) {
            issue.getRelatedFacts().addAll(findFacts(request.relatedFactIds()));
        }
        if (request.relatedEvidenceIds() != null) {
            issue.getRelatedEvidence().addAll(findEvidence(request.relatedEvidenceIds()));
        }
        return IssueDto.from(issueRepository.save(issue));
    }

    public List<IssueDto> list(UUID workstreamId, UUID callerId) {
        workstreamService.requireAccess(workstreamId, callerId);
        return issueRepository.findByWorkstreamId(workstreamId).stream().map(IssueDto::from).toList();
    }

    public IssueDto get(UUID issueId, UUID callerId) {
        Issue issue = findIssue(issueId);
        workstreamService.requireAccess(issue.getWorkstream().getId(), callerId);
        return IssueDto.from(issue);
    }

    @Transactional
    public IssueDto update(UUID issueId, UpdateIssueRequest request, UUID callerId) {
        Issue issue = findIssue(issueId);
        workstreamService.requireAccess(issue.getWorkstream().getId(), callerId);
        User owner = request.ownerUserId() != null ? findUser(request.ownerUserId()) : null;
        issue.update(request.title(), request.description(), request.severity(), request.status(),
                owner, request.dueDate(), request.resolution());
        return IssueDto.from(issue);
    }

    private Issue findIssue(UUID issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private Set<Fact> findFacts(List<UUID> ids) {
        return Set.copyOf(factRepository.findAllById(ids));
    }

    private Set<Evidence> findEvidence(List<UUID> ids) {
        return Set.copyOf(evidenceRepository.findAllById(ids));
    }
}
