CREATE TABLE if not exists price_bar (
                           id BIGSERIAL PRIMARY KEY,
                           asset_id BIGINT NOT NULL REFERENCES asset(id) ON DELETE CASCADE,
                           ts DATE NOT NULL,
                           open NUMERIC(18,6),
                           high NUMERIC(18,6),
                           low  NUMERIC(18,6),
                           close NUMERIC(18,6) NOT NULL,
                           volume BIGINT,

                           CONSTRAINT uq_price_bar_asset_ts UNIQUE (asset_id, ts)
);

CREATE INDEX idx_price_bar_asset_ts ON price_bar(asset_id, ts);
