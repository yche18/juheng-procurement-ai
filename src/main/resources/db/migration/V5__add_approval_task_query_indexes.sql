CREATE INDEX idx_approval_task_assignee_created_id
    ON approval_task (assignee_id, created_at DESC, id DESC);

CREATE INDEX idx_approval_task_assignee_status_created_id
    ON approval_task (assignee_id, status, created_at DESC, id DESC);
