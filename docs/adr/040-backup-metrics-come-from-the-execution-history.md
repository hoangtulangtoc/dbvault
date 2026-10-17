# ADR-040: Backup metrics come from the execution history

**Status**: Accepted
**Date**: 2026-10
**Context for**: DBV-30

## Context

Operators run dbvault next to their monitoring and want backup duration, size
and failures as metrics. The console already shows the same facts, read from
`backup_executions`.

## Decision

`/actuator/prometheus` (Micrometer) serves four gauges per target, tagged
`target` (its name) and `engine`: the finish time, duration and size of its
newest successful backup, and the number of its backups that failed in the last
24 hours. They are computed from the execution history by a refresh every 30
seconds, not counted as events in memory, so a restart loses nothing and the
numbers agree with the console. A scrape never waits on the database.

The endpoint uses the API's security chain: HTTP Basic with the operator
account, no session. `/actuator/health` stays public and says only UP or DOWN.

The optional `observability` compose profile runs Prometheus and Grafana with a
provisioned dashboard. Neither publishes a port.

## Consequences

- Target names become label values. There is one series per target, so the
  cardinality is the number of targets.
- A gauge moves at most every 30 seconds; an alert on failures should not
  expect more precision than that.
- Restore metrics and per-run histograms are left out until someone asks.
