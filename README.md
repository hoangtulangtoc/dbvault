# dbvault

Logical backup and restore for MySQL, driven from a web console, with a command-line client
in [dbvault-cli](https://gitlab.com/dbvault1/dbvault-cli).

Register a database as a target, test the connection, take a full logical backup that is
compressed as it is written, and restore it after an explicit confirmation. Backups are kept on
local disk; target passwords are stored encrypted.

> dbvault is under active development. PostgreSQL, MongoDB, S3-compatible storage and schedules
> are planned; see the project board.

## Build

Java 21 and Docker (the integration tests start real databases with Testcontainers), plus the
MySQL client tools on the `PATH`:

```bash
mvn verify
```

## Layout

| Module | Holds |
| --- | --- |
| `core` | The domain and its ports. No framework. |
| `adapters` | Persistence, the database clients, storage. |
| `application` | The use cases. |
| `web` | The composition root and the console. |

See [docs/](docs/) for the architecture and the decisions behind it.

## Contributing

[CONTRIBUTING.md](CONTRIBUTING.md) explains how work is planned, branched and released;
[docs/branching.md](docs/branching.md) has the exact git steps.
