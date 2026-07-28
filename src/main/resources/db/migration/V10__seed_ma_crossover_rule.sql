INSERT INTO alert_rule (rule_key, name, enabled)
VALUES ('MA_CROSSOVER_20_50', 'SMA 20/50 crossover', TRUE)
    ON CONFLICT (rule_key) DO NOTHING;
