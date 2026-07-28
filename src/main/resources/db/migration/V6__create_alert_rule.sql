CREATE TABLE alert_rule (
                            id BIGSERIAL PRIMARY KEY,
                            rule_key VARCHAR(50) NOT NULL UNIQUE,  -- VOL_SPIKE_20, DRAWDOWN_10PCT
                            name VARCHAR(120) NOT NULL,
                            enabled BOOLEAN NOT NULL DEFAULT TRUE,

    -- Generic numeric parameters (keep MVP flexible)
                            threshold NUMERIC(20,10),
                            window_days INT,
                            multiplier NUMERIC(20,10),

                            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
