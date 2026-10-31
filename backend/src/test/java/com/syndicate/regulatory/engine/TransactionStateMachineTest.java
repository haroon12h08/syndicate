package com.syndicate.regulatory.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.audit.AuditService;
import com.syndicate.common.ConflictException;
import com.syndicate.conflict.FactConflictRepository;
import com.syndicate.permission.Permission;
import com.syndicate.permission.PermissionService;
import com.syndicate.regulatory.engine.dto.TransitionBlockerDto;
import com.syndicate.regulatory.engine.dto.TransitionRequest;
import com.syndicate.regulatory.engine.dto.TransitionResultDto;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionRepository;
import com.syndicate.transaction.TransactionService;
import com.syndicate.transaction.TransactionType;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionStateMachineTest {

    @Mock private TransactionService transactionService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ControlEvaluationRepository evaluationRepository;
    @Mock private RegulatoryExceptionRepository exceptionRepository;
    @Mock private FactConflictRepository conflictRepository;
    @Mock private TransactionLifecycleTransitionRepository transitionRepository;
    @Mock private PermissionService permissionService;
    @Mock private AuditService auditService;

    private TransactionStateMachineService stateMachineService;
    private User testUser;
    private Transaction testTransaction;
    private RegulatoryControl control;
    private ControlEvaluation failedEval;

    @BeforeEach
    void setUp() {
        stateMachineService = new TransactionStateMachineService(
                transactionService,
                transactionRepository,
                evaluationRepository,
                exceptionRepository,
                conflictRepository,
                transitionRepository,
                permissionService,
                auditService,
                new ObjectMapper()
        );

        testUser = new User("haroon@trinitycapital.com", "hash", "Haroon Rasheed");
        ReflectionTestUtils.setField(testUser, "id", UUID.randomUUID());

        testTransaction = new Transaction(null, null, TransactionType.SME_IPO, "Test SME IPO");
        ReflectionTestUtils.setField(testTransaction, "id", UUID.randomUUID());
        testTransaction.setLifecycleState(TransactionLifecycleState.DOCUMENT_PREPARATION);

        Requirement req = new Requirement("SEBI_ICDR_238", "SEBI", "INDIA", "MPC", "Desc", "SME_IPO", "ALL", "CRITICAL", "ACTIVE");
        RequirementVersion ver = new RequirementVersion(req, 1, "Cit", "https://sebi.gov.in", LocalDate.now(), testUser);
        control = new RegulatoryControl(ver, "CTRL_SEBI_MPC_20PCT", "MPC 20%", "Desc", "DOMAIN", "{}", "CRITICAL", 1);
        ReflectionTestUtils.setField(control, "id", UUID.randomUUID());

        failedEval = new ControlEvaluation(testTransaction, control, null, "FAIL", "{}", "{}", "18.5%", "Failed MPC", null);
    }

    @Test
    @DisplayName("Transition to INTERNAL_APPROVAL is refused when unexempted regulatory control failures exist")
    void transition_isRefused_whenUnexemptedBlockersExist() {
        when(transactionService.findTransaction(testTransaction.getId())).thenReturn(testTransaction);
        when(evaluationRepository.findByTransactionId(testTransaction.getId())).thenReturn(List.of(failedEval));
        when(exceptionRepository.findByTransactionIdAndStatus(testTransaction.getId(), "APPROVED")).thenReturn(List.of());
        when(conflictRepository.findByTransactionIdAndStatus(testTransaction.getId(), com.syndicate.conflict.ConflictStatus.OPEN)).thenReturn(List.of());

        TransitionRequest request = new TransitionRequest(TransactionLifecycleState.INTERNAL_APPROVAL, "Ready for board review", false);

        assertThatThrownBy(() -> stateMachineService.transition(testTransaction.getId(), request, testUser))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot advance to INTERNAL_APPROVAL: 1 blocking prerequisite(s) remain unresolved");
    }

    @Test
    @DisplayName("Transition succeeds when blocker is covered by an approved regulatory exception")
    void transition_succeeds_whenBlockerHasApprovedException() {
        when(transactionService.findTransaction(testTransaction.getId())).thenReturn(testTransaction);
        when(evaluationRepository.findByTransactionId(testTransaction.getId())).thenReturn(List.of(failedEval));

        RegulatoryException exception = new RegulatoryException(
                testTransaction, control, control.getRequirementVersion(),
                "Promoter undertaking approved", "INTERNAL_APPROVAL", null, "MEDIUM",
                testUser, LocalDate.now().plusMonths(1), "Escrow account created"
        );
        exception.setStatus("APPROVED");
        ReflectionTestUtils.setField(exception, "id", UUID.randomUUID());

        when(exceptionRepository.findByTransactionIdAndStatus(testTransaction.getId(), "APPROVED")).thenReturn(List.of(exception));
        when(conflictRepository.findByTransactionIdAndStatus(testTransaction.getId(), com.syndicate.conflict.ConflictStatus.OPEN)).thenReturn(List.of());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        TransitionRequest request = new TransitionRequest(TransactionLifecycleState.INTERNAL_APPROVAL, "Advancing under board exception", true);
        TransitionResultDto result = stateMachineService.transition(testTransaction.getId(), request, testUser);

        assertThat(result.allowed()).isTrue();
        assertThat(result.toState()).isEqualTo(TransactionLifecycleState.INTERNAL_APPROVAL);
        assertThat(testTransaction.getLifecycleState()).isEqualTo(TransactionLifecycleState.INTERNAL_APPROVAL);
        verify(transitionRepository).save(any(TransactionLifecycleTransition.class));
    }
}
