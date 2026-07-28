# Alert delivery setup

When an alert fires, the app fans it out to every **enabled** notification channel and
records the result per channel in the `alert_delivery` table. There are two channels:
**webhook** and **email**. Each one is only active when its config is present — with
nothing configured the app still runs and evaluates alerts, it just doesn't send them
anywhere.

Credentials are read from **environment variables** (never commit them). The
`application.yml` keys below already point at these env vars.

## Webhook (Slack / Discord / generic)

Set one variable:

| Env var              | Example                                             |
|----------------------|-----------------------------------------------------|
| `ALERTS_WEBHOOK_URL` | `https://hooks.slack.com/services/T000/B000/XXXX`   |

The channel POSTs a Slack-compatible `{"text": "..."}` body, so a Slack or Discord
incoming webhook works out of the box. Leave the variable unset to disable it.

## Email (SMTP)

Email needs an SMTP server **and** a recipient. Both must be set or the channel stays off:

| Env var             | Purpose                          | Example                       |
|---------------------|----------------------------------|-------------------------------|
| `MAIL_HOST`         | SMTP host (enables email)        | `smtp.gmail.com`              |
| `MAIL_PORT`         | SMTP port (default `587`)        | `587`                         |
| `MAIL_USERNAME`     | SMTP login                       | `you@gmail.com`               |
| `MAIL_PASSWORD`     | SMTP password / app password     | `abcd efgh ijkl mnop`         |
| `ALERTS_EMAIL_TO`   | recipient address                | `you@gmail.com`               |
| `ALERTS_EMAIL_FROM` | from address (has a default)     | `alerts@market-sentinel.local`|

### Gmail specifics

Gmail rejects your normal password over SMTP. Turn on 2-Step Verification, then create an
**App Password** (Google Account → Security → App passwords) and use that 16-character
value as `MAIL_PASSWORD`. Host `smtp.gmail.com`, port `587` (STARTTLS, already enabled).

## Setting the variables

PowerShell (current session):

```powershell
$env:ALERTS_WEBHOOK_URL = "https://hooks.slack.com/services/..."
$env:MAIL_HOST     = "smtp.gmail.com"
$env:MAIL_USERNAME = "you@gmail.com"
$env:MAIL_PASSWORD = "your-app-password"
$env:ALERTS_EMAIL_TO = "you@gmail.com"
./mvnw spring-boot:run
```

bash:

```bash
export ALERTS_WEBHOOK_URL="https://hooks.slack.com/services/..."
export MAIL_HOST="smtp.gmail.com"
export MAIL_USERNAME="you@gmail.com"
export MAIL_PASSWORD="your-app-password"
export ALERTS_EMAIL_TO="you@gmail.com"
./mvnw spring-boot:run
```

## Verifying it works

1. Start the app with the vars set. On boot nothing sends yet.
2. Trigger an alert evaluation for an asset that has data, e.g.
   `POST /api/v1/assets/{id}/alerts/evaluate` (see `requests.http`).
3. A **new** alert (one not already in `alert_event` for that day) publishes a
   notification after the transaction commits. Watch the app log for
   `[NotificationDispatcher] SENT webhook ...` / `SENT email ...`.
4. Inspect outcomes in the DB:
   ```sql
   SELECT * FROM alert_delivery ORDER BY created_at DESC;
   ```
   `status` is `SENT` or `FAILED` (with the error in `detail`).

Note: alerts are idempotent per `(asset, rule, day)`, so re-running evaluate on the same
day won't re-fire (and won't re-send). To test repeatedly, use different assets/days or
clear the day's `alert_event` rows.
