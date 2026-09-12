package com.syndicate.notification;

import com.syndicate.common.BaseEntity;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "task_id")
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationPriority priority;

    @Column(nullable = false)
    private String title;

    @Column
    private String body;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(User user, UUID transactionId, UUID taskId, NotificationType type,
                        NotificationPriority priority, String title, String body) {
        this.user = user;
        this.transactionId = transactionId;
        this.taskId = taskId;
        this.type = type;
        this.priority = priority;
        this.title = title;
        this.body = body;
    }

    public void markRead() {
        this.readAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
