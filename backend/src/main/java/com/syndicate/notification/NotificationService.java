package com.syndicate.notification;

import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.notification.dto.NotificationDto;
import com.syndicate.task.TransactionTask;
import com.syndicate.transaction.TransactionMembership;
import com.syndicate.transaction.TransactionMembershipRepository;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationService {

    /** The seats that can actually fix a missing-advisor problem by sending an invitation. */
    private static final Set<TransactionRole> DEAL_LEADS =
            Set.of(TransactionRole.ISSUER_ADMIN, TransactionRole.LEAD_BANKER);

    private final NotificationRepository notificationRepository;
    private final TransactionMembershipRepository membershipRepository;

    public NotificationService(NotificationRepository notificationRepository,
                                TransactionMembershipRepository membershipRepository) {
        this.notificationRepository = notificationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> listMine(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationDto::from)
                .toList();
    }

    @Transactional
    public NotificationDto markRead(UUID notificationId, UUID callerId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!notification.getUser().getId().equals(callerId)) {
            throw new ResourceNotFoundException("Notification not found: " + notificationId);
        }
        notification.markRead();
        return NotificationDto.from(notification);
    }

    @Transactional
    public void notifyTaskAssigned(User assignee, TransactionTask task) {
        if (alreadySent(assignee.getId(), task.getId(), NotificationType.TASK_ASSIGNED)) {
            return;
        }
        notificationRepository.save(new Notification(
                assignee,
                task.getTransaction().getId(),
                task.getId(),
                NotificationType.TASK_ASSIGNED,
                NotificationPriority.NORMAL,
                "Task assigned: " + task.getTitle(),
                "You hold the "
                        + (task.getRequiredRole() != null ? task.getRequiredRole().name() : "required")
                        + " seat on " + task.getTransaction().getName() + "."));
    }

    /**
     * Nobody on the deal holds the seat this task needs, so the leads are told to invite the
     * missing advisory firm — they are the only ones who can unblock it.
     */
    @Transactional
    public void notifyLeadsOfMissingRole(TransactionTask task) {
        String role = task.getRequiredRole() != null ? task.getRequiredRole().name() : "an advisor";
        String workstream = task.getWorkstream() != null ? task.getWorkstream().getType().name() : "this transaction";

        for (TransactionMembership membership :
                membershipRepository.findByTransactionId(task.getTransaction().getId())) {
            if (!DEAL_LEADS.contains(membership.getRole())) {
                continue;
            }
            if (alreadySent(membership.getUser().getId(), task.getId(),
                    NotificationType.TASK_ESCALATED_MISSING_ROLE)) {
                continue;
            }
            notificationRepository.save(new Notification(
                    membership.getUser(),
                    task.getTransaction().getId(),
                    task.getId(),
                    NotificationType.TASK_ESCALATED_MISSING_ROLE,
                    NotificationPriority.HIGH,
                    "Unassigned task needs " + role,
                    "No one on this transaction holds the " + role + " seat required for " + workstream
                            + ". Invite the advisory firm to unblock \"" + task.getTitle() + "\"."));
        }
    }

    private boolean alreadySent(UUID userId, UUID taskId, NotificationType type) {
        return taskId != null && notificationRepository.existsByUserIdAndTaskIdAndType(userId, taskId, type);
    }
}
