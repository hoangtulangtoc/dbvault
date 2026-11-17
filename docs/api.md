# HTTP API

The API is for scripts and dbvault-cli. It uses the console's operator account,
sent with HTTP Basic on every request; it creates no session. Version 1 lives
under `/api/v1` ([ADR-039](adr/039-http-api-is-versioned-under-api-v1.md)).

| Method and path | Answer |
| --- | --- |
| `GET /api/v1/targets` | Every target, without its password; `tlsRequired` is `false` for a target that opted out of TLS |
| `GET /api/v1/targets/{id}` | One target, with the same fields as the list; `404` for an unknown ID |
| `GET /api/v1/targets/{id}/backups` | One target's backups, newest first, without their path on the server; `?status=FAILED` (or `RUNNING`, `SUCCEEDED`, any case) keeps only those, and an unknown status is a `400`; `?limit=N` keeps the newest `N` of them, and a limit below 1, or one that is not a whole number, is a `400` |
| `POST /api/v1/backups/{id}/verifications` | `202` and the new restore-verification attempt; `Location` is where to follow it |
| `GET /api/v1/backups/{backupId}/verifications/{id}` | One attempt: `status` is `RUNNING`, `SUCCEEDED` or `FAILED`; `404` when it belongs to another backup |
| `GET /api/v1/targets/{id}/retention/preview?keep=N` | The backups a retention policy keeping `N` successful backups would delete if it ran now, newest first; deletes nothing. `keep` below 1 is a `400` |

Errors are `{"error": "..."}`: `401` without valid credentials, `404` for an
unknown target, backup or attempt, `409` when a verification cannot start.
Restore verification must be enabled for the deployment; see
[deployment.md](deployment.md).

```sh
curl -u admin https://dbvault.example.com/api/v1/targets
curl -u admin "https://dbvault.example.com/api/v1/targets/<target-id>/backups?status=FAILED&limit=5"
curl -u admin -X POST https://dbvault.example.com/api/v1/backups/<backup-id>/verifications
```

The second line asks for a target's five most recent failed backups: `status` filters first,
then `limit` keeps the newest of what is left.

## Previewing a retention policy

Before lowering how many backups a target keeps, ask what the new number would delete. The
preview reads the same list retention acts on after a successful backup, and deletes nothing:

```sh
curl -u admin "https://dbvault.example.com/api/v1/targets/<target-id>/retention/preview?keep=7"
```

```json
[
  {"id": "0b6f…", "status": "SUCCEEDED", "startedAt": "2026-11-08T01:00:00Z", "finishedAt": "2026-11-08T01:00:41Z", "sizeBytes": 61301298},
  {"id": "9c1d…", "status": "SUCCEEDED", "startedAt": "2026-11-07T01:00:00Z", "finishedAt": "2026-11-07T01:00:39Z", "sizeBytes": 61287004}
]
```

An empty list means keeping that many would delete nothing. Only successful backups count
towards `keep`. The answer is true at the moment it is given: retention runs after each
successful backup, so the next one to finish can change what is deleted.

The unversioned `/api/targets` paths of dbvault 0.2 are gone: they answer `404`.

`GET /api/v1/verifications/{id}` is gone since dbvault 1.2.0 and answers `404`: follow an
attempt at `GET /api/v1/backups/{backupId}/verifications/{id}`, where `Location` points.
dbvault-cli 1.1 and older still use the old path, so upgrade the CLI with the server
([upgrading-to-1.2.md](upgrading-to-1.2.md)).
