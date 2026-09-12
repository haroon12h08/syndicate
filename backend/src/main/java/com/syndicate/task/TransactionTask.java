package com.syndicate.task;

import com.syndicate.common.BaseEntity;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transaction_tasks")
public class TransactionTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workstream_id")
    private Workstream workstream;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_role")
    private TransactionRole requiredRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @Column(name = "escalated_to_leads", nullable = false)
    private boolean escalatedToLeads;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "source_rule_id")
    private UUID sourceRuleId;

    @Column(name = "source_issue_id")
    private UUID sourceIssueId;

    protected TransactionTask() {
    }

    public TransactionTask(Transaction transaction, Workstream workstream, String title, String description,
                           IssueSeverity severity, TransactionRole requiredRole, LocalDate dueDate,
                           User createdByUser) {
        this.transaction = transaction;
        this.workstream = workstream;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.requiredRole = requiredRole;
        this.dueDate = dueDate;
        this.createdByUser = createdByUser;
        this.status = TaskStatus.TODO;
    }

    /** Routed to a member holding the required role. */
    public void assignTo(User user) {
        this.assignedUser = user;
        this.escalatedToLeads = false;
        if (this.status == TaskStatus.UNASSIGNED_ESCALATED) {
            this.status = TaskStatus.TODO;
        }
    }

    /** No member holds the required role, so the leads are asked to bring one in. */
    public void escalate() {
        this.assignedUser = null;
        this.status = TaskStatus.UNASSIGNED_ESCALATED;
        this.escalatedToLeads = true;
    }

    public void moveTo(TaskStatus status) {
        this.status = status;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }

    public void refreshFromRule(String title, String description, IssueSeverity severity) {
        this.title = title;
        this.description = description;
        this.severity = severity;
    }

    public void reopenIfSettled() {
        if (status == TaskStatus.RESOLVED) {
            status = assignedUser != null ? TaskStatus.TODO : TaskStatus.UNASSIGNED_ESCALATED;
        }
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Workstream getWorkstream() {
        return workstream;
    }

    public void setWorkstream(Workstream workstream) {
        this.workstream = workstream;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public TransactionRole getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(TransactionRole requiredRole) {
        this.requiredRole = requiredRole;
    }

    public User getAssignedUser() {
        return assignedUser;
    }

    public boolean isEscalatedToLeads() {
        return escalatedToLeads;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public UUID getSourceRuleId() {
        return sourceRuleId;
    }

    public void setSourceRuleId(UUID sourceRuleId) {
        this.sourceRuleId = sourceRuleId;
    }

    public UUID getSourceIssueId() {
        return sourceIssueId;
    }

    public void setSourceIssueId(UUID sourceIssueId) {
        this.sourceIssueId = sourceIssueId;
    }
}
