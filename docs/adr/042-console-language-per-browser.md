# ADR-042: The console's language is chosen per browser

**Status**: Accepted
**Date**: 2026-10
**Refines**: ADR-011 (one operator account)

## Context

Operators asked for a Vietnamese console (DBV-31). There is one shared operator
account (ADR-011), so a language stored on the account would be one language
for everybody who signs in with it. Every word of the console was written into
its templates and controllers.

## Decision

- Wording lives in `messages.properties` (English) and `messages_vi.properties`
  (Vietnamese), with the same keys; a test fails when they differ. Spring's
  message source reads them with `fallback-to-system-locale: false`, so a key
  missing from Vietnamese shows in English rather than in the server's locale.
- The language is the browser's, not the account's: `?lang=en` or `?lang=vi` on
  any console page (a GET) switches it and is kept in the `dbvault_lang` cookie
  (`HttpOnly`, `SameSite=Lax`, one year); without a cookie, `Accept-Language`
  decides. Anything else asked for is English. The sidebar and the sign-in page
  link to both languages, each written in its own.
- `<html lang>` follows the language, so a screen reader reads the page in it.
- Scripts carry no wording of their own: a label they swap comes from a
  `data-` attribute the page rendered in its language.
- The HTTP API, the CLI, logs, notifications and stored error messages stay
  English. They are read by scripts and by whoever reads logs, not by the
  operator in the console.

## Consequences

- Every console page is translated: the shell, targets, storage, notification
  channels and subscriptions, schedules, retention, backups, restores and the
  error page, with the confirmation dialogs they open, the form checks
  (validation messages resolve `{key}` through the same bundle) and the flash
  messages, which controllers write in the language of the request that set
  them. Wording a script shows (the live job follower) comes from `data-`
  attributes the page rendered. DBV-31 is done: the first slice covered the
  shell, the Targets pages, the Backups list, status badges and the pager; the
  second covered the rest.
- A pattern with arguments goes through `MessageFormat`, so a quote in it is
  written twice (`''{0}''`); a test formats every such pattern in both bundles.
  Byte counts use `{0,number,#}` so they keep the exact figure without grouping.
- Error text from the engines' own clients (a MySQL "Access denied", for
  example) is shown as the client wrote it, in every language.
