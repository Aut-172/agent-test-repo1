CREATE TABLE work_orders (
    id VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(10000) NOT NULL,
    asset_id VARCHAR(100) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    reported_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledgement_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    resolution_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    acknowledged_by VARCHAR(100),
    started_at TIMESTAMP WITH TIME ZONE,
    assigned_engineer VARCHAR(100),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_summary VARCHAR(4000),
    closed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancellation_reason VARCHAR(4000),
    version BIGINT NOT NULL
);

CREATE INDEX idx_work_orders_asset_created ON work_orders (asset_id, created_at);
CREATE INDEX idx_work_orders_status_priority ON work_orders (status, priority);
CREATE INDEX idx_work_orders_ack_deadline ON work_orders (acknowledgement_deadline);
CREATE INDEX idx_work_orders_resolution_deadline ON work_orders (resolution_deadline);

CREATE TABLE work_order_audit_records (
    id VARCHAR(36) PRIMARY KEY,
    work_order_id VARCHAR(36) NOT NULL,
    action VARCHAR(100) NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload_json VARCHAR(10000) NOT NULL,
    CONSTRAINT fk_audit_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id)
);

CREATE INDEX idx_audit_work_order_time ON work_order_audit_records (work_order_id, occurred_at);

CREATE TABLE outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_json VARCHAR(10000) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_outbox_work_order FOREIGN KEY (aggregate_id) REFERENCES work_orders(id)
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (published_at, occurred_at);
