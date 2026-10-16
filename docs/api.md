# HTTP API

The API is for scripts and dbvault-cli. It uses the console's operator account,
sent with HTTP Basic on every request; it creates no session. Version 1 lives
under `/api/v1` ([ADR-039](adr/039-http-api-is-versioned-under-api-v1.md)).

| Method and path | Answer |
| --- | --- |
| `GET /api/v1/targets` | Every target, without its password |
| `GET /api/v1/targets/{id}/backups` | One target's backups, newest first, without their path on the server |
| `POST /api/v1/backups/{id}/verifications` | `202` and the new restore-verification attempt; `Location` is where to follow it |
| `GET /api/v1/verifications/{id}` | One attempt: `status` is `RUNNING`, `SUCCEEDED` or `FAILED` |

Errors are `{"error": "..."}`: `401` without valid credentials, `404` for an
unknown target, backup or attempt, `409` when a verification cannot start.
Restore verification must be enabled for the deployment; see
[deployment.md](deployment.md).

```sh
curl -u admin https://dbvault.example.com/api/v1/targets
curl -u admin -X POST https://dbvault.example.com/api/v1/backups/<backup-id>/verifications
```

The unversioned `/api/targets` paths answer `308 Permanent Redirect` to
`/api/v1` and are marked deprecated.
