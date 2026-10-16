# ADR-039: The HTTP API is versioned under /api/v1

**Status**: Accepted
**Date**: 2026-10
**Refines**: the read-only `/api` added for dbvault-cli

## Context

Scripts and dbvault-cli read targets and backups from `/api/targets`. The API is
about to grow (restore verification, DBV-26) and will change shape again later.
With no version in the path, any such change breaks every client at once.

## Decision

The API lives under `/api/v1`. Version 1 is what `/api` served, plus restore
verification:

- `GET /api/v1/targets`
- `GET /api/v1/targets/{id}/backups`
- `POST /api/v1/backups/{id}/verifications` → `202`, `Location` of the attempt
- `GET /api/v1/verifications/{id}`

The unversioned paths answer `308 Permanent Redirect` to their `/api/v1` path,
with `Deprecation: true` and a `successor-version` link, until they are removed
in the same release. A later incompatible change gets `/api/v2`; `/api/v1` then
keeps its behaviour.

The API chain keeps HTTP Basic on every request and creates no session. It
reads no cookie, so CSRF protection, which guards cookie-authenticated
requests, is off for it; the console's chain keeps it.

## Consequences

- A client that does not follow redirects, dbvault-cli 0.2 included, must use
  `/api/v1` itself. This is a breaking change for such scripts.
- Errors stay `{"error": "..."}`: `404` for an unknown target, backup or attempt,
  `409` when a verification cannot start (disabled, unsupported, unavailable, or
  a backup that did not succeed).
