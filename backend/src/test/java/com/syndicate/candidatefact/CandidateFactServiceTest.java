package com.syndicate.candidatefact;

import com.syndicate.candidatefact.dto.AcceptCandidateFactRequest;
import com.syndicate.candidatefact.dto.RejectCandidateFactRequest;
import com.syndicate.common.BadRequestException;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateFactServiceTest {

    @Mock CandidateFactRepository candidateFactRepository;
    @Mock FactRepository factRepository;
    @Mock WorkstreamService workstreamService;
    @Mock Workstream workstream;
    @Mock Evidence evidence;
    @Mock User caller;

    CandidateFactService service;
    UUID workstreamId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CandidateFactService(candidateFactRepository, factRepository, workstreamService);
        when(workstream.getId()).thenReturn(workstreamId);
        when(caller.getId()).thenReturn(callerId);
    }

    private CandidateFact pendingCandidate() {
        CandidateFact candidate = new CandidateFact(workstream, evidence, "Revenue", "100", "FY2026",
                1, 10, 10, 5, 5, 1000, 1400, CandidateFactSource.TEXT_LAYER);
        return candidate;
    }

    @Test
    void acceptCreatesARealFactLinkedToTheSourceEvidence() {
        CandidateFact candidate = pendingCandidate();
        UUID candidateId = UUID.randomUUID();
        when(candidateFactRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(factRepository.save(any(Fact.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.accept(candidateId,
                new AcceptCandidateFactRequest(null, "42,18,000", null, null, "confirmed"), caller);

        assertThat(result.status()).isEqualTo(CandidateFactStatus.ACCEPTED);
        assertThat(candidate.getStatus()).isEqualTo(CandidateFactStatus.ACCEPTED);
        assertThat(candidate.getResultingFact()).isNotNull();
        assertThat(candidate.getResultingFact().getValue()).isEqualTo("42,18,000");
        assertThat(candidate.getResultingFact().getEvidence()).contains(evidence);
        assertThat(candidate.getReviewedByUser()).isEqualTo(caller);
    }

    @Test
    void acceptFallsBackToCandidateFieldsWhenNoOverrideGiven() {
        CandidateFact candidate = pendingCandidate();
        UUID candidateId = UUID.randomUUID();
        when(candidateFactRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(factRepository.save(any(Fact.class))).thenAnswer(inv -> inv.getArgument(0));

        service.accept(candidateId, new AcceptCandidateFactRequest(null, null, null, null, null), caller);

        assertThat(candidate.getResultingFact().getLabel()).isEqualTo("Revenue");
        assertThat(candidate.getResultingFact().getValue()).isEqualTo("100");
        assertThat(candidate.getResultingFact().getPeriod()).isEqualTo("FY2026");
    }

    @Test
    void rejectMarksCandidateRejectedWithoutCreatingAFact() {
        CandidateFact candidate = pendingCandidate();
        UUID candidateId = UUID.randomUUID();
        when(candidateFactRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        var result = service.reject(candidateId, new RejectCandidateFactRequest("not relevant"), caller);

        assertThat(result.status()).isEqualTo(CandidateFactStatus.REJECTED);
        verifyNoInteractions(factRepository);
    }

    @Test
    void cannotAcceptAnAlreadyReviewedCandidate() {
        CandidateFact candidate = pendingCandidate();
        candidate.reject(caller, "already handled");
        UUID candidateId = UUID.randomUUID();
        when(candidateFactRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.accept(candidateId,
                new AcceptCandidateFactRequest(null, null, null, null, null), caller))
                .isInstanceOf(BadRequestException.class);
    }
}
