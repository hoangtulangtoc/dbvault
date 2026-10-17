# ADR-041: Connections to targets require TLS unless a target opts out

**Status**: Accepted
**Date**: 2026-10
**Refines**: ADR-003, ADR-017, ADR-018 (how each engine's client is called)

## Context

Every backup, restore and connection test sends a target's password, and then
the whole dump, between dbvault and the database server. Each client's default
lets that happen in clear text: the MySQL client and libpq *prefer* TLS and fall
back to plain text without a word when the server does not offer it, and the
MongoDB database tools do not encrypt unless asked. A target on another host or
network was therefore backed up unencrypted whenever its server allowed it, and
nothing in the console said so (DBV-33).

## Decision

A target has `tlsRequired`, true unless the operator clears it. It is a target
setting, not a deployment one, because one dbvault often backs up servers of
different ages.

- A new target requires TLS: the form's box is ticked, and a target built with
  nothing said about TLS requires it.
- Migration V14 adds `database_targets.tls_required` with default `TRUE`, so
  **existing targets require TLS after the upgrade**. A server that cannot speak
  TLS then fails its next connection test and backup until its target is opted
  out; the v1.0 upgrade guide says so.
- Changing it drops the last connection check, like any other change to how a
  target connects.
- The target list marks an opted-out target **TLS not required**, and its edit
  form shows a warning while it stays opted out.

What each client is told, always explicitly so that an option file or variable
on the host cannot decide for a target:

| Engine | Required | Opted out |
| --- | --- | --- |
| MySQL (`mysql`, `mysqldump`) | `--ssl-mode=REQUIRED` | `--ssl-mode=PREFERRED` |
| PostgreSQL (`psql`, `pg_dump`, `pg_restore`) | `PGSSLMODE=require` | `PGSSLMODE=prefer` |
| MongoDB (`mongodump`, `mongorestore`) | `--tls` | no TLS option |

## Consequences

- Required means encrypted, not authenticated. `REQUIRED` and `require` accept
  any certificate, including the one MySQL 8 generates for itself, so they stop
  passive eavesdropping and not an active man in the middle. Checking the
  server's identity needs a CA per target, which a later slice can add as
  `VERIFY_CA`/`verify-full`.
- MongoDB's tools have no "prefer": `--tls` also checks the server's certificate
  against the image's CA store, so a MongoDB server with a self-signed
  certificate must be opted out until a target can carry its CA.
- Restore verification is unchanged: its throwaway containers have no published
  port and are reached over the Docker host's own network (ADR-029).
- The MySQL integration tests now run with TLS required against MySQL 8.4 and
  check the session's cipher; the PostgreSQL and MongoDB test containers do not
  speak TLS, so their tests opt out.
