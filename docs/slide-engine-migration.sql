-- Run only if the deployment does not use Hibernate ddl-auto=update.
CREATE TABLE IF NOT EXISTS slide_orders (
    id VARCHAR(255) PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    request_key VARCHAR(80) NOT NULL,
    cost INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_slide_order_request UNIQUE (chat_id, request_key)
);
CREATE INDEX IF NOT EXISTS idx_slide_order_state_time ON slide_orders(status, updated_at);
