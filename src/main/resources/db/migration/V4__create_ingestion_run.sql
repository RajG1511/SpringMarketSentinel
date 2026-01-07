CREATE TABLE ingestion_run (
                               id BIGSERIAL PRIMARY KEY,
                               asset_id BIGINT NOT NULL REFERENCES asset(id) ON DELETE CASCADE,
                               started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                               finished_at TIMESTAMPTZ,
                               status VARCHAR(20) NOT NULL, -- SUCCESS / FAILED
                               inserted INT NOT NULL DEFAULT 0,
                               skipped INT NOT NULL DEFAULT 0,
                               message TEXT
);

CREATE INDEX idx_ingestion_run_asset_started ON ingestion_run(asset_id, started_at DESC);
