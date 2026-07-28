CREATE TABLE alert_delivery (
                                id BIGSERIAL PRIMARY KEY,
                                alert_event_id BIGINT NOT NULL REFERENCES alert_event(id) ON DELETE CASCADE,
                                channel VARCHAR(20) NOT NULL,  -- webhook / email
                                status VARCHAR(20) NOT NULL,   -- SENT / FAILED
                                detail TEXT,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_alert_delivery_event ON alert_delivery(alert_event_id, created_at);
