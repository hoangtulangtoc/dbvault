# Upgrading to 1.2

dbvault 1.2 runs on the same metadata database as 1.1 and needs no migration
step of its own. One change needs attention before the upgrade: **the HTTP API
no longer serves `GET /api/v1/verifications/{id}`**, so dbvault-cli 1.1 and any
script that polls a restore verification there stop working.

## Who is affected

- **dbvault-cli `verify`.** Version 1.1 and older poll the removed path: against
  a 1.2 server the first poll answers `404` and `verify` exits with `4`.
  dbvault-cli 1.2 polls the new path, and needs a 1.2 server for it.
- **Your own scripts**, if they call `GET /api/v1/verifications/<id>`. A script
  that follows the `Location` of `POST /api/v1/backups/<backup-id>/verifications`
  is not affected: it already points at the new path.

Nothing else in the API changes, and the console is not affected.

## Upgrade

1. Find scripts that use the old path:

   ```sh
   grep -rn 'api/v1/verifications/' <your scripts>
   ```

   Change each to `GET /api/v1/backups/<backup-id>/verifications/<verification-id>`,
   or make it follow `Location`. The new path answers `404` for an attempt of
   another backup.

2. Upgrade the server:

   ```sh
   git pull                     # or check out the v1.2.0 tag
   docker compose up -d --build
   ```

3. Upgrade dbvault-cli to 1.2 on every machine that runs `dbvault verify`, at
   the same time as the server. Check it:

   ```sh
   dbvault --version            # dbvault-cli 1.2.0
   dbvault verify --backup-id <backup-id>
   ```

## Going back

Going back to 1.1 needs nothing for the database. Put dbvault-cli 1.1 back
with it: dbvault-cli 1.2 cannot follow a verification on a 1.1 server.
