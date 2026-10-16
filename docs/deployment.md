# Deployment

One image, one compose file. See
[ADR-009](adr/009-one-image-that-carries-the-mysql-client.md) for the original
packaging decision and [ADR-017](adr/017-route-logical-backups-by-database-engine.md)
for the PostgreSQL client added to it. MongoDB packaging is recorded in
[ADR-018](adr/018-mongodb-archives-and-explicit-authentication-database.md).

## What the image contains

A JRE, the application jar, Ubuntu's `mysql-client` and `postgresql-client`,
and MongoDB's `mongodb-database-tools`. The MySQL package is Oracle's MySQL,
not MariaDB. That
distinction is load-bearing: MariaDB's `mysqldump` rejects
`--set-gtid-purged`, which this tool always passes, so every MySQL backup would
fail. The PostgreSQL package supplies `psql`, `pg_dump` and `pg_restore`; the
MongoDB package supplies `mongodump` and `mongorestore`. Anyone changing the
base image must check all seven binaries again.

It runs as an unprivileged user, `dbvault` (uid 10001), and its healthcheck
asks `/actuator/health`, so it only reports healthy once the application is up
and the metadata store answers. That endpoint needs no sign-in and says UP or
DOWN, nothing more.

## What an operator has to decide

**The encryption key.** `ENCRYPTION_SECRET_KEY` has no default and the
application will not start without it. Losing it makes every stored target
password unrecoverable; there is no rotation mechanism.

**The console password.** There is one account
([ADR-011](adr/011-one-operator-account-from-the-environment.md)):
`OPERATOR_USERNAME`, `admin` unless set, and `OPERATOR_PASSWORD_HASH`, which
has no default. It is a bcrypt hash, never the password:

```bash
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 ""
```

prints `:$2y$12$…` — drop the leading colon. In `.env`, put it in single
quotes, `OPERATOR_PASSWORD_HASH='$2y$12$…'`; unquoted, compose reads each `$`
as the start of a variable. Anything that is not a bcrypt hash of cost 10 or
more stops the application at startup. Unlike the encryption key, losing it
costs nothing: generate a new hash and restart.

**Where the backups go.** Local filesystem is the built-in default; compose
stores it in the `backups` named volume. The Storage page can add an
S3-compatible profile and a target can select it for future backups. The bucket
must already exist. Use HTTPS in production; HTTP custom endpoints are intended
for development. Static secrets are AES-256-GCM encrypted, while default-chain
mode can use environment, web-identity, container or instance-role credentials.

The S3 identity needs Put, Get, Head and Delete plus multipart upload and abort
permissions for its bucket/prefix. Configure a bucket lifecycle rule to abort
incomplete multipart uploads left by a process crash. Bucket creation,
encryption policy and object-version cleanup remain operator responsibilities.
`STORAGE_STAGING_DIR` needs room for one complete artifact per concurrent S3
job; normal, failed and known interrupted operation directories are removed.

**Retention starts disabled.** Configure it per target in the Retention page to
keep the newest N successful backups. It runs only after a new successful
backup; saving a policy or restarting does not immediately prune existing
files. Backups with restore history, failed attempts and running jobs stay
outside automatic cleanup. Without a policy the volume still grows until an
operator deletes backups through the console. See
[ADR-024](adr/024-retention-keeps-new-unrestored-backups-per-target.md).

**Notifications are target-scoped and best-effort.** Create reusable Slack,
Email or generic Webhook channels on the Notifications page, send a real
test, then select channels and events from each target. Slack and webhook
URLs are encrypted with `ENCRYPTION_SECRET_KEY`. Restore events use the
destination target's subscriptions. Delivery failures are logged and shown by
Send test, but never change the result of a backup or restore.

Email needs deployment-wide SMTP configuration. Set `DBVAULT_SMTP_HOST`, and
as needed `DBVAULT_SMTP_PORT`, `DBVAULT_SMTP_USERNAME`,
`DBVAULT_SMTP_PASSWORD`, `DBVAULT_SMTP_FROM`, `DBVAULT_SMTP_AUTH` and
`DBVAULT_SMTP_STARTTLS`. Leaving the host empty is valid and keeps the
application healthy; only Email delivery remains unavailable. All SMTP socket
timeouts are five seconds. See
[ADR-028](adr/028-target-scoped-notification-channels.md).

**Restore verification is deliberately opt-in.** Set
`DBVAULT_VERIFICATION_ENABLED=true` to expose **Test restore** and the
per-target automatic checkbox. MySQL, PostgreSQL and MongoDB verification
needs the host Docker socket: uncomment
the socket volume and `group_add` in `docker-compose.yml`, then set
`DOCKER_SOCKET_GID` to `stat -c '%g' /var/run/docker.sock` on the host. The
application image already carries the Docker CLI.

Mounting `/var/run/docker.sock` grants root-equivalent control over the Docker
host. Keep verification disabled when that trust boundary is unacceptable;
startup and every other feature remain available. Verification containers
publish no ports, receive no source-database credentials, use deterministic
names and are force-removed before an attempt may succeed. Operators can
override the three images and the pull, startup and cleanup timeouts with the
`DBVAULT_VERIFICATION_*` variables listed in `docker-compose.yml`. See
[ADR-029](adr/029-isolated-restore-verification.md).

**Metrics are a scrape away, monitoring is optional.** `/actuator/prometheus`
serves dbvault's backup metrics to anything that signs in with the operator
account over HTTP Basic, like dbvault-cli. One series per target, tagged with
its name and engine: `dbvault_backup_last_success_timestamp_seconds`,
`dbvault_backup_last_duration_seconds` and `dbvault_backup_last_size_bytes` of
its newest successful backup, and `dbvault_backup_failures_24h`. They are read
from the execution history every 30 seconds, so they survive a restart. The
`observability` compose profile adds Prometheus and Grafana with a provisioned
"dbvault backups" dashboard; write the two password files first
(`observability/prometheus/dbvault-password`, the console password, and
`observability/grafana/admin-password`), then
`docker compose --profile observability up -d`. Neither service publishes a
port; map Grafana's 3000 in an override file to open it. See
[ADR-040](adr/040-backup-metrics-come-from-the-execution-history.md).

**Schedules use the application's clock and one explicit zone each.** Cron
expressions use Quartz syntax, with seconds as the first field. The schedule
row persists in metadata PostgreSQL and its in-memory Quartz trigger is rebuilt
at startup. A fire missed while the application is down is skipped rather than
replayed; after restart the next future fire is shown in the console. This
deployment is intentionally single-instance for scheduling — running several
application replicas would create the same trigger in each replica.

**Reaching the databases to be backed up.** A MySQL, PostgreSQL or MongoDB server on the
Docker host is `host.docker.internal` from inside the container; compose maps
that name explicitly because on Linux it does not otherwise exist. A server
elsewhere just needs to be routable from the container.

**Connections to targets are encrypted.** Every target requires TLS unless its
form's **Require TLS** box is cleared: the clients are told `--ssl-mode=REQUIRED`
(MySQL), `PGSSLMODE=require` (PostgreSQL) or `--tls` (MongoDB), and refuse a
server that will not encrypt. Upgrading to 1.0 switches existing targets to
required too, so a server without TLS fails its next connection test until it
is given TLS or its target is opted out; the list marks such a target **TLS not
required**. Required means encrypted, not that the server's certificate is
checked — except for MongoDB, whose tools check it against the image's CA store
([ADR-041](adr/041-connections-to-targets-require-tls.md)).

**The console speaks English or Vietnamese.** Each browser picks: the sidebar
and the sign-in page link to both, `?lang=vi` on any page does the same, and the
choice is kept in a cookie; without one the browser's language decides. The API,
logs and notifications stay English. Not every page is translated yet
([ADR-042](adr/042-console-language-per-browser.md)).

**Metadata PostgreSQL is not a target.** The `postgres` service in compose holds
the application's target and execution records. It is not offered as a backup
target automatically. Backing it up requires registering a PostgreSQL target
explicitly, with credentials that have the required access.

**Client paths.** The image sets `MYSQL_CLIENT_PATH`, `MYSQLDUMP_PATH`,
`PSQL_PATH`, `PG_DUMP_PATH`, `PG_RESTORE_PATH`, `MONGODUMP_PATH` and
`MONGORESTORE_PATH` to `/usr/bin/...`. A source or custom-image deployment may
override them, but every configured file must be executable or startup fails.

MongoDB credentials may belong to a database other than the one being backed
up. The registration form therefore asks for an authentication database and
defaults it to `admin`. Passwords are handed to each tool through a temporary
owner-only config file, never through its visible command line.

PostgreSQL's dump tools have major-version compatibility rules: a client that
can read a source is not necessarily able to produce an archive loadable by an
older destination. The first PostgreSQL slice does not install or select among
several client majors. Use `pg_dump --version` and provide `PSQL_PATH`,
`PG_DUMP_PATH` and `PG_RESTORE_PATH` from a client release compatible with both
the source and destination; the integration test proves a matching client and
server major end to end.

## What it does and does not protect

Every page needs a sign-in, and every form carries a CSRF token, so neither a
visitor to port 8080 nor a page open in an operator's browser can start a
restore or delete a backup ([ADR-011](adr/011-one-operator-account-from-the-environment.md)).
What is still up to the deployment:

**No TLS.** The console speaks plain HTTP, so the password and the session
cookie cross the network in clear text unless something encrypts them. Put a
TLS-terminating reverse proxy in front, and then set:

- `SESSION_COOKIE_SECURE=true`, so the browser sends the session cookie only
  over HTTPS;
- `SERVER_FORWARD_HEADERS_STRATEGY=native`, so the application believes the
  proxy's `X-Forwarded-*` headers. Without it the application thinks every
  request arrived over plain HTTP: its redirects — to the sign-in page, and
  after every form — point at `http://`, HSTS is never sent, and the sign-in
  log shows the proxy's address instead of the client's. Only set it when the
  proxy is the only way in; otherwise a client can claim any address.

**No limit on sign-in attempts.** Each guess costs a bcrypt check, which is
slow, but nothing stops a patient attacker. Failed attempts are logged as
`Sign-in failed for '…' from <address>`; rate limiting belongs in the proxy.

**One shared account.** The history says a restore was started, not by whom.

Even signed in, this is a tool that can overwrite production data. Keep it
somewhere only trusted operators can reach — behind a VPN, or bound to a
private interface — and do not expose it to the internet.
