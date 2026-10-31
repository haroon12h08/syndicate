package com.syndicate.regulatory.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.audit.AuditService;
import com.syndicate.common.ForbiddenException;
import com.syndicate.regulatory.engine.dto.RequirementTestCaseDto;
import com.syndicate.regulatory.engine.dto.RequirementVersionDto;
import com.syndicate.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleGovernanceTest {

    @Mock private RequirementRepository requirementRepository;
    @Mock private RequirementVersionRepository versionRepository;
    @Mock private RequirementTestCaseRepository testCaseRepository;
    @Mock private RegulatoryControlRepository controlRepository;
    @Mock private AuditService auditService;

    private RegulatoryGovernanceService governanceService;
    private User author;
    private User approver;
    private Requirement requirement;
    private RequirementVersion version;

    @BeforeEach
    void setUp() {
        governanceService = new RegulatoryGovernanceService(
                requirementRepository,
                versionRepository,
                testCaseRepository,
                controlRepository,
                auditService,
                new ObjectMapper()
        );

        author = new User("author@trinitycapital.com", "hash", "Alice Author");
        ReflectionTestUtils.setField(author, "id", UUID.randomUUID());

        approver = new User("approver@almadinacapital.com", "hash", "Bob Approver");
        ReflectionTestUtils.setField(approver, "id", UUID.randomUUID());

        requirement = new Requirement("SEBI_SME_CAPITAL_TEST", "SEBI", "INDIA", "Title", "Desc", "SME_IPO", "ALL", "CRITICAL", "ACTIVE");
        ReflectionTestUtils.setField(requirement, "id", UUID.randomUUID());

        version = new RequirementVersion(requirement, 1, "Citation", "https://sebi.gov.in", LocalDate.now(), author);
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("Four-eyes governance invariant: Rule author cannot approve their own requirement version")
    void approveRequirement_rejectsAuthorAsApprover() {
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> governanceService.approveRequirementVersion(version.getId(), author))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Four-eyes governance invariant violation: Rule author (Alice Author) cannot approve their own regulatory requirement version");
    }

    @Test
    @DisplayName("Different authorized user can approve requirement version and move it to ACTIVE")
    void approveRequirement_allowsIndependentApprover() {
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(versionRepository.save(any(RequirementVersion.class))).thenAnswer(i -> i.getArgument(0));
        when(testCaseRepository.findByRequirementVersionId(version.getId())).thenReturn(List.of());

        RequirementVersionDto approved = governanceService.approveRequirementVersion(version.getId(), approver);

        assertThat(approved.status()).isEqualTo("ACTIVE");
        assertThat(approved.approvedById()).isEqualTo(approver.getId());
    }

    @Test
    @DisplayName("Requirement test cases run deterministic fixture assertions")
    void executeTestCases_evaluatesFixturesDeterministically() {
        RequirementTestCase testCase = new RequirementTestCase(
                version,
                "Valid Capital Under 25 Cr",
                "{\"issue.post_issue_paid_up_capital\": 150000000}",
                "PASS"
        );
        ReflectionTestUtils.setField(testCase, "id", UUID.randomUUID());

        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(testCaseRepository.findByRequirementVersionId(version.getId())).thenReturn(List.of(testCase));
        when(testCaseRepository.save(any(RequirementTestCase.class))).thenAnswer(i -> i.getArgument(0));

        List<RequirementTestCaseDto> results = governanceService.executeTestCases(version.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).lastRunPassed()).isTrue();
    }
}
