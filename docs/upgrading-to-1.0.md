# Upgrading to 1.0

dbvault 1.0 runs on the same metadata database as 0.3 and migrates it on its
first start. One change needs an operator's attention before the first
scheduled backup after the upgrade: **every target now requires TLS**.

Coming from 0.2? Upgrade to 0.3 first and read its notes: the HTTP API moved
under `/api/v1`, and dbvault-cli 0.2 cannot follow it.

## Before you upgrade

1. **Back up the metadata database.** Flyway migrates forward only; going back
   to 0.3 means restoring this backup.

   ```sh
   docker compose exec postgres pg_dump -U dbvault -Fc dbvault > dbvault-metadata-0.3.dump
   ```

2. **Find out which database servers speak TLS.** For each target, from a
   machine on the same network:

   | Engine | Check |
   | --- | --- |
   | MySQL | `mysql --ssl-mode=REQUIRED -h <host> -P <port> -u <user> -p -e "SHOW SESSION STATUS LIKE 'Ssl_cipher'"` prints a cipher |
   | PostgreSQL | `PGSSLMODE=require psql -h <host> -p <port> -U <user> -d <db> -c "SHOW ssl"` connects |
   | MongoDB | `mongosh --tls --host <host> --port <port> -u <user> --authenticationDatabase <db> --eval 1` connects |

   MongoDB checks the server's certificate against the image's CA store; a
   server with a self-signed certificate fails this check and will have to be
   opted out for now.

3. Let running backups and restores finish. Nothing is lost if one is cut off —
   it is marked failed on the next start — but it will have to be run again.

## Upgrade

```sh
git pull                     # or check out the v1.0.0 tag
docker compose up -d --build
```

On start, migration `V14` adds `database_targets.tls_required`, **true for every
existing target**.

## After you upgrade

1. **Test every target** from the Targets page. A server that does not offer
   TLS now fails with the client's own words (for MySQL, `SSL is required but
   the server doesn't support it`), and so would its next scheduled backup.
2. For a server that cannot be given TLS yet, **Edit** the target and clear
   **Require TLS for every connection**. Its password and dumps then cross the
   network unencrypted; the list marks it **TLS not required** until the box is
   ticked again. Test it once more.
3. Run one backup by hand, or wait for the next schedule, and check it succeeds.

## What else is new

None of these needs action.

- **Metrics.** `/actuator/prometheus` serves backup metrics to the operator
  account; the optional `observability` compose profile adds Prometheus and a
  Grafana dashboard ([deployment.md](deployment.md)).
- **Vietnamese console.** Each browser can switch between English and
  Vietnamese; the remaining pages follow in later releases
  ([ADR-042](adr/042-console-language-per-browser.md)).
- **Licence report.** The jar lists the libraries it bundles and their licences
  ([licences.md](licences.md)).
- **API.** `GET /api/v1/targets` adds `tlsRequired`; nothing was removed or
  renamed, so 0.3 scripts keep working.
- **dbvault-cli 1.0.** Every command takes `--output json`. The CLI follows the
  server's version numbers; 0.3 still works against a 1.0 server.

## Going back to 0.3

Stop the application, restore the metadata dump into an empty database, and
start the 0.3 image:

```sh
docker compose stop app
docker compose exec -T postgres sh -c 'dropdb -U dbvault dbvault && createdb -U dbvault dbvault'
docker compose exec -T postgres pg_restore -U dbvault -d dbvault < dbvault-metadata-0.3.dump
git checkout v0.3.0 && docker compose up -d --build app
```

Backups taken by 1.0 stay where they were written and remain restorable by 0.3:
the artifact formats did not change.
