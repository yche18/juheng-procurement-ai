CREATE INDEX idx_procurement_request_creator_created_id
    ON procurement_request (creator_id, created_at DESC, id DESC);

CREATE INDEX idx_procurement_request_creator_status_created_id
    ON procurement_request (creator_id, status, created_at DESC, id DESC);
