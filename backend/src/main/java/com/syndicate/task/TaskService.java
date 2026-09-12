package com.syndicate.task;

import com.syndicate.common.BadRequestException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.notification.NotificationService;
import com.syndicate.task.dto.CreateTaskRequest;
import com.syndicate.task.dto.TaskDto;
import com.syndicate.task.dto.UpdateTaskRequest;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionMembership;
import com.syndicate.transaction.TransactionMembershipRepository;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import com.syndicate.user.UserRepository;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamRepository;
import com.syndicate.workstream.WorkstreamType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TaskService {

    /**
     * Which seat is expected to own work arising in each workstream. Used to route engine-created
     * tasks; manual tasks may state their own required role instead.
     */
    private static final Map<WorkstreamType, TransactionRole> DEFAULT_ROLE_BY_WORKSTREAM =
            new EnumMap<>(WorkstreamType.class);

    static {
        DEFAULT_ROLE_BY_WORKSTREAM.put(WorkstreamType.FINANCIAL_DUE_DILIGENCE, TransactionRole.AUDITOR);
        DEFAULT_ROLE_BY_WORKSTREAM.put(WorkstreamType.LEGAL_DUE_DILIGENCE, TransactionRole.LEAD_LAWYER);
        DEFAULT_ROLE_BY_WORKSTREAM.put(WorkstreamType.LITIGATION, TransactionRole.LEAD_LAWYER);
        DEFAULT_ROLE_BY_WORKSTREAM.put(WorkstreamType.MATERIAL_CONTRACTS, TransactionRole.LEAD_LAWYER);
        DEFAULT_ROLE_BY_WORKSTREAM.put(WorkstreamType.CAPITAL_STRUCTURE, TransactionRole.LEAD_BANKER);
        DEFAULT_ROLE_BY_WORKSTREAM.put(WorkstreamType.TRANSACTION_READINESS, TransactionRole.LEAD_BANKER);
        DEFAULT_ROLE_BY_WORKSTREAM.put(WorkstreamType.BUSINESS_DUE_DILIGENCE, TransactionRole.DUE_DILIGENCE_TEAM);
        DEFAULT_ROLE_BY_WORKSTREAM.put(WorkstreamType.REGULATORY_DUE_DILIGENCE, TransactionRole.REGULATORY_CONSULTANT);
        DEFAULT_ROLE_BY_WORKSTREAM.put(WorkstreamType.CORPORATE_SECRETARIAL, TransactionRole.COMPANY_SECRETARY);
        DEFAULT_ROLE_BY_WORKSTREAM.put(WorkstreamType.TAX, TransactionRole.TAX_ADVISOR);
    }

    private final TransactionTaskRepository taskRepository;
    private final TransactionMembershipRepository membershipRepository;
    private final WorkstreamRepository workstreamRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;
    private final NotificationService notificationService;

    public TaskService(TransactionTaskRepository taskRepository,
                        TransactionMembershipRepository membershipRepository,
                        WorkstreamRepository workstreamRepository,
                        UserRepository userRepository,
                        TransactionService transactionService,
                        NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.membershipRepository = membershipRepository;
        this.workstreamRepository = workstreamRepository;
        this.userRepository = userRepository;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> list(UUID transactionId, UUID workstreamId, TaskStatus status,
                               IssueSeverity severity, UUID assignedUserId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return taskRepository.findByTransactionId(transactionId).stream()
                .filter(t -> workstreamId == null
                        || (t.getWorkstream() != null && t.getWorkstream().getId().equals(workstreamId)))
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> severity == null || t.getSeverity() == severity)
                .filter(t -> assignedUserId == null
                        || (t.getAssignedUser() != null && t.getAssignedUser().getId().equals(assignedUserId)))
                .sorted(Comparator.comparing(TransactionTask::getCreatedAt))
                .map(TaskDto::from)
                .toList();
    }

    @Transactional
    public TaskDto create(UUID transactionId, CreateTaskRequest request, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        Transaction transaction = transactionService.findTransaction(transactionId);

        Workstream workstream = null;
        if (request.workstreamId() != null) {
            workstream = workstreamRepository.findById(request.workstreamId())
                    .orElseThrow(() -> new ResourceNotFoundException("Workstream not found: " + request.workstreamId()));
            if (!workstream.getTransaction().getId().equals(transactionId)) {
                throw new BadRequestException("That workstream belongs to a different transaction");
            }
        }

        TransactionRole requiredRole = request.requiredRole() != null
                ? request.requiredRole()
                : (workstream != null ? DEFAULT_ROLE_BY_WORKSTREAM.get(workstream.getType()) : null);

        TransactionTask task = new TransactionTask(transaction, workstream, request.title(), request.description(),
                request.severity() != null ? request.severity() : IssueSeverity.MEDIUM,
                requiredRole, request.dueDate(), caller);
        route(task);
        return TaskDto.from(taskRepository.save(task));
    }

    @Transactional
    public TaskDto update(UUID taskId, UpdateTaskRequest request, User caller) {
        TransactionTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        transactionService.requireMembership(task.getTransaction().getId(), caller.getId());

        if (request.assignedUserId() != null) {
            User assignee = userRepository.findById(request.assignedUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.assignedUserId()));
            if (!membershipRepository.existsByTransactionIdAndUserId(
                    task.getTransaction().getId(), assignee.getId())) {
                throw new BadRequestException("That person is not a member of this transaction");
            }
            task.assignTo(assignee);
            notificationService.notifyTaskAssigned(assignee, task);
        }

        if (request.status() != null) {
            if (request.status() == TaskStatus.UNASSIGNED_ESCALATED) {
                throw new BadRequestException("Escalation is set by the engine, not by hand");
            }
            if (task.getAssignedUser() == null && request.status() != TaskStatus.TODO) {
                throw new BadRequestException("Assign this task before moving it beyond To do");
            }
            task.moveTo(request.status());
        }

        if (request.resolutionNote() != null) {
            task.setResolutionNote(request.resolutionNote());
        }
        return TaskDto.from(task);
    }

    /**
     * Creates or refreshes the task a failing regulatory rule owns. Called by the readiness engine,
     * so it must be idempotent: one task per rule per transaction, updated in place.
     */
    @Transactional
    public void syncRuleTask(Transaction transaction, UUID ruleId, WorkstreamType workstreamType,
                              String title, String description, IssueSeverity severity,
                              UUID sourceIssueId, boolean stillBlocking, User caller) {
        Optional<TransactionTask> existing =
                taskRepository.findByTransactionIdAndSourceRuleId(transaction.getId(), ruleId);

        if (!stillBlocking) {
            existing.ifPresent(task -> {
                task.setResolutionNote("Closed automatically: the rule now passes.");
                task.moveTo(TaskStatus.RESOLVED);
            });
            return;
        }

        Workstream workstream = workstreamRepository.findByTransactionId(transaction.getId()).stream()
                .filter(w -> w.getType() == workstreamType)
                .findFirst()
                .orElse(null);

        TransactionTask task = existing.orElseGet(() -> {
            TransactionTask fresh = new TransactionTask(transaction, workstream, title, description, severity,
                    DEFAULT_ROLE_BY_WORKSTREAM.get(workstreamType), null, caller);
            fresh.setSourceRuleId(ruleId);
            return taskRepository.save(fresh);
        });

        task.refreshFromRule(title, description, severity);
        task.setSourceIssueId(sourceIssueId);
        if (task.getWorkstream() == null && workstream != null) {
            task.setWorkstream(workstream);
        }
        if (task.getRequiredRole() == null) {
            task.setRequiredRole(DEFAULT_ROLE_BY_WORKSTREAM.get(workstreamType));
        }
        task.reopenIfSettled();
        route(task);
    }

    /**
     * Routes a task to whoever holds the required seat. When nobody does, the task is escalated to
     * the deal leads rather than silently sitting unassigned — the missing advisor is the real
     * blocker, and only the leads can invite one.
     */
    private void route(TransactionTask task) {
        if (task.getAssignedUser() != null) {
            return;
        }
        TransactionRole requiredRole = task.getRequiredRole();
        if (requiredRole == null) {
            return;
        }

        Optional<TransactionMembership> holder =
                membershipRepository.findByTransactionId(task.getTransaction().getId()).stream()
                        .filter(m -> m.getRole() == requiredRole)
                        .min(Comparator.comparing(m -> m.getUser().getId()));

        if (holder.isPresent()) {
            task.assignTo(holder.get().getUser());
            notificationService.notifyTaskAssigned(holder.get().getUser(), task);
        } else {
            task.escalate();
            notificationService.notifyLeadsOfMissingRole(task);
        }
    }

    public static TransactionRole defaultRoleFor(WorkstreamType type) {
        return DEFAULT_ROLE_BY_WORKSTREAM.get(type);
    }
}
