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

- The first slice translates the shell (navigation, sign-in, theme and
  language switches), the Targets list and form, the Backups list, execution
  status badges and the pager. The other pages, the confirmation dialogs they
  open, validation messages and flash messages still speak English until they
  move to keys; DBV-31 stays open for them.
- Error text from the engines' own clients (a MySQL "Access denied", for
  example) is shown as the client wrote it, in every language.
