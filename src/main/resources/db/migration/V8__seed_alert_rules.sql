INSERT INTO alert_rule (rule_key, name, enabled, window_days, multiplier)
VALUES ('VOL_SPIKE_20', 'Volatility spike (20d)', TRUE, 180, 1.8)
    ON CONFLICT (rule_key) DO NOTHING;

INSERT INTO alert_rule (rule_key, name, enabled, window_days, threshold)
VALUES ('DRAWDOWN_10PCT', 'Drawdown <= -10% (60d peak)', TRUE, 60, -0.10)
    ON CONFLICT (rule_key) DO NOTHING;
