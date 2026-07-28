CREATE TABLE alert_event (
                             id BIGSERIAL PRIMARY KEY,
                             asset_id BIGINT NOT NULL REFERENCES asset(id) ON DELETE CASCADE,
                             rule_id BIGINT NOT NULL REFERENCES alert_rule(id) ON DELETE CASCADE,
                             ts DATE NOT NULL,

                             severity VARCHAR(20) NOT NULL, -- INFO/WARN/CRITICAL
                             message TEXT NOT NULL,

                             created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

                             CONSTRAINT uq_alert_event UNIQUE(asset_id, rule_id, ts)
);

CREATE INDEX idx_alert_event_asset_ts ON alert_event(asset_id, ts DESC);
CREATE INDEX idx_alert_event_rule_ts ON alert_event(rule_id, ts DESC);
