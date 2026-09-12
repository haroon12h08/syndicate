-- Task engine with role-based routing plus the shared notification table.

CREATE TABLE transaction_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    workstream_id UUID REFERENCES workstreams(id),
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2048),
    status VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    required_role VARCHAR(64),
    assigned_user_id UUID REFERENCES users(id),
    escalated_to_leads BOOLEAN NOT NULL DEFAULT FALSE,
    resolution_note VARCHAR(2048),
    due_date DATE,
    created_by_user_id UUID REFERENCES users(id),
    -- Set when the readiness engine owns this task, so re-evaluation updates instead of duplicating.
    source_rule_id UUID REFERENCES regulatory_rules(id),
    source_issue_id UUID REFERENCES issues(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_task_per_rule UNIQUE (transaction_id, source_rule_id)
);

CREATE INDEX idx_tasks_transaction ON transaction_tasks (transaction_id);
CREATE INDEX idx_tasks_workstream ON transaction_tasks (workstream_id);
CREATE INDEX idx_tasks_assigned_user ON transaction_tasks (assigned_user_id);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    transaction_id UUID REFERENCES transactions(id),
    task_id UUID REFERENCES transaction_tasks(id) ON DELETE CASCADE,
    type VARCHAR(64) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(2048),
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, read_at);
