CREATE TABLE metric_value (
                              id BIGSERIAL PRIMARY KEY,
                              asset_id BIGINT NOT NULL REFERENCES asset(id) ON DELETE CASCADE,
                              ts DATE NOT NULL,
                              metric_type VARCHAR(50) NOT NULL,
                              value NUMERIC(20,10) NOT NULL,
                              CONSTRAINT uq_metric_value UNIQUE (asset_id, ts, metric_type)
);

CREATE INDEX idx_metric_value_asset_ts ON metric_value(asset_id, ts);
CREATE INDEX idx_metric_value_asset_type_ts ON metric_value(asset_id, metric_type, ts);
