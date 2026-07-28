# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot "market sentinel": it ingests daily price bars for tracked assets, computes analytics (returns, moving average, volatility), and evaluates alert rules (volatility spike, drawdown). It is a resume/portfolio project built in phases against `roadmap.txt` — read that file for the intended end-state (streaming, notifications, dashboard) and to see which phase upcoming work belongs to.

## Commands

The app requires a running Postgres. Bring up infra first:

```bash
docker compose up -d            # Postgres on :5433 (db market_sentinel / ms_user / ms_pass), Redis on :6379
```

Build / run / test (use the Maven wrapper):

```bash
./mvnw spring-boot:run          # run the app on :8080 (Flyway migrates on startup)
./mvnw clean package            # build the jar (runs tests)
./mvnw test                     # run all tests
./mvnw test -Dtest=SpringMarketAnalysisApplicationTests#contextLoads   # single test
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

- Swagger UI: `http://localhost:8080/swagger-ui` — the fastest way to exercise endpoints.
- `requests.http` is a ready-made click-through of the full pipeline (create asset → ingest → metrics → alerts).
- Health: `http://localhost:8080/actuator/health`.

Tests currently require a database (the only test is `@SpringBootTest contextLoads`, which starts the full context and connects to Postgres). There is no test-slice or in-memory config yet, so `./mvnw test` needs `docker compose up` running.

## Architecture

The whole product is a **pipeline that runs per-asset in a fixed order**: ingest prices → compute metrics → evaluate alerts. That ordering is the key invariant — metrics read `price_bar`, and alerts read both `metric_value` and `price_bar`, so metrics must run before alerts, and both must run after ingestion. Two entry points drive this pipeline:

1. `scheduler/MarketIngestionJob` — the `@Scheduled` cron job (`market.scheduler.cron`, default weekday 18:10) loops over every asset and runs all three stages, catching exceptions per-asset so one failure doesn't halt the batch.
2. The manual REST endpoints (below) let you trigger each stage independently for one asset, which is how you develop and demo.

Each stage is a `@Transactional` service returning a small result record:

- `service/PriceIngestionService` — fetches bars via a `PriceProvider`, inserts only missing `(asset, ts)` rows (idempotent upsert), and records every attempt as an `ingestion_run` (start → markSuccess/markFailure).
- `metric/MetricsService` — recomputes `RETURN_1D`, `SMA_20`, `SMA_50`, `VOL_20` over a lookback window and inserts only missing `(asset, ts, metric_type)` rows. All math is `BigDecimal` with a shared `MathContext(20, HALF_UP)`; volatility uses a hand-rolled Newton sqrt.
- `alert/AlertsService` — anchors on the latest `price_bar` date, loads enabled `alert_rule` rows, and dispatches on `rule_key` (`VOL_SPIKE_20`, `DRAWDOWN_10PCT`, `MA_CROSSOVER_20_50`) in a `switch`. Alert dedup is idempotent on `(asset, rule, ts)`.

**Adding an alert rule** means two coordinated changes: seed a row in a new Flyway migration (see `V8__seed_alert_rules.sql`) with the right `rule_key` and params, and add a matching `case` in `AlertsService.evaluateAlertsForAsset`. A rule row with no matching `case` is silently ignored.

### Alert notifications (`notification/`)

When `AlertsService` writes a *new* `alert_event`, it publishes an `AlertNotification` application event. `NotificationDispatcher` consumes it with `@TransactionalEventListener(AFTER_COMMIT)` in a fresh `REQUIRES_NEW` transaction — so notifications only go out for alerts that actually committed, and network I/O never runs inside the alert transaction. It fans out to every `NotificationChannel` bean whose `isEnabled()` is true and records one `alert_delivery` row (SENT/FAILED) per channel.

`NotificationChannel` is the same Strategy shape as `PriceProvider`: `WebhookNotificationChannel` (Slack-compatible POST, enabled when `market.alerts.webhook.url` is set) and `EmailNotificationChannel` (SMTP, enabled only when `spring.mail.host` + a recipient are set — it injects `JavaMailSender` via `ObjectProvider` so the app boots fine with no mail configured). Add a channel by implementing the interface; no other wiring needed. Credentials/config are documented in `EMAIL_SETUP.md`.

### Price providers (Strategy pattern)

`marketdata/PriceProvider` has two implementations — `StooqPriceProvider` (CSV, default, no key) and `AlphaVantagePriceProvider` (JSON, needs `ALPHAVANTAGE_API_KEY`). `PriceProviderConfig` picks one at startup from `market.prices.provider` and exposes it as the single `PriceProvider` bean, so the rest of the app is provider-agnostic. To add a provider, implement the interface and extend the switch in `PriceProviderConfig`.

### Persistence

- Postgres is the source of truth. **Schema is owned by Flyway migrations** in `src/main/resources/db/migration` (`V1`..`V8`); JPA runs with `ddl-auto: validate`, so entities must match the migrations exactly or the app fails to boot. Never change schema via entities alone — add a new `V{n}__*.sql`.
- All time-series tables enforce idempotency with a unique constraint: `price_bar(asset_id, ts)`, `metric_value(asset_id, ts, metric_type)`, `alert_event(asset_id, rule_id, ts)`. The services rely on these — inserts check existence first rather than upserting in SQL.
- `ts` on time-series rows is a `LocalDate` (daily close), while audit timestamps (`created_at`, `started_at`) are `Instant`.

### Web layer

Controllers are thin and split by domain under `/api/v1/assets/{assetId}/...` (assets, prices, metrics, alerts) plus `/api/v1/admin/ingest/prices`. `api/GlobalExceptionHandler` maps exceptions to a common `ApiError` JSON body. Note the current mapping quirk: "not found" is thrown as `IllegalArgumentException` → **400**, not 404, even though a `NotFoundException` → 404 handler exists but is unused.

## Config

`application.yml` holds the knobs: `market.prices.provider`, `market.metrics.lookbackDays`, `market.scheduler.{enabled,cron}`, and `market.alphavantage.apiKey` (from `ALPHAVANTAGE_API_KEY`). Set `market.scheduler.enabled: false` to disable the cron job (guarded by `@ConditionalOnProperty`).

Alert delivery is env-driven and each channel self-disables when unset: `ALERTS_WEBHOOK_URL` for the webhook, and `MAIL_HOST`/`MAIL_USERNAME`/`MAIL_PASSWORD` + `ALERTS_EMAIL_TO` for email (full table in `EMAIL_SETUP.md`).

**Caching:** `GET /assets/{id}/metrics/latest` is Redis-cached via `@Cacheable` (`CacheConfig`, cache name `latestMetrics`, key = assetId, TTL `market.cache.ttlSeconds` default 300s) and evicted by `@CacheEvict` whenever `computeMetricsForAsset` runs. Cache values are JSON, written by a serializer bound to the cache's value type (`Jackson2JsonRedisSerializer<LatestMetrics>`) with no polymorphic type information embedded — so a forged Redis value can't choose which class the app instantiates. Each cache is registered explicitly via `withCacheConfiguration`, and `disableCreateOnMissingCache()` makes an unregistered cache name fail fast rather than silently fall back to JDK serialization; adding a cache means adding an entry in `CacheConfig`. Redis connects lazily via `spring.data.redis.host/port` (`REDIS_HOST`/`REDIS_PORT`), so the app still boots and reads fall through to the DB when Redis is down.

The cache manager is built on a `RedisCacheWriter` configured with `immediateWrites()`. This is deliberate: with Lettuce, Spring Data Redis 4.0 defaults cache writes to fire-and-forget, so `put` returns before Redis applies the SET and a read right after a miss can miss again (and write failures are swallowed). `MetricsCacheIntegrationTest.cacheWritesAreVisibleImmediately` guards this — without it that test fails and `secondReadIsServedFromCache` goes flaky.
