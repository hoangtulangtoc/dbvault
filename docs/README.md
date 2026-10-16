# Documentation

- [architecture/overview.md](architecture/overview.md) — the four modules and
  why the dependency direction is what it is.
- [storage.md](storage.md) — keeping backups in S3-compatible object storage:
  profiles, the bucket permissions they need, and how uploads and restores use staging.
- [deployment.md](deployment.md) — the image, the compose file, and what an
  operator has to decide.
- [api.md](api.md) — the HTTP API under `/api/v1` for scripts and dbvault-cli.
- [branching.md](branching.md) — GitFlow: which branch comes from where, and
  the exact steps for a release and a hotfix.
- [adr/](adr/) — architecture decision records, numbered from 001 in the order
  the decisions were made.

The current last decision is
[ADR-025](adr/025-s3-storage-profiles-and-local-staging.md), which defines
provider-neutral artifact references, managed S3 profiles and local staging,
by
[ADR-033](adr/033-local-artifacts-live-per-target-and-execution.md), which
gives every local artifact its own `<target-id>/<execution-id>/` directory so
two backups can never share a file, by
[ADR-028](adr/028-target-scoped-notification-channels.md), which adds reusable
notification channels and per-target backup/restore event subscriptions with
best-effort bounded delivery, and by
[ADR-029](adr/029-isolated-restore-verification.md), which verifies successful
backups in disposable databases without changing the backup outcome.

These pages describe the code that exists. When a slice changes the code, it
changes these files in the same commit.
