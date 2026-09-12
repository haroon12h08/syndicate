package com.syndicate.notification.dto;

import com.syndicate.notification.Notification;
import com.syndicate.notification.NotificationPriority;
import com.syndicate.notification.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        UUID transactionId,
        UUID taskId,
        NotificationType type,
        NotificationPriority priority,
        String title,
        String body,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getTransactionId(), n.getTaskId(), n.getType(),
                n.getPriority(), n.getTitle(), n.getBody(), n.getReadAt(), n.getCreatedAt());
    }
}
