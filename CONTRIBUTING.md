# Contributing

## Where work comes from

Product work is planned in Jira (project **DBV**): every user-visible change starts as a story
or a bug there, with a fix version. Infrastructure and operations work lives in Linear (team
**OPS**). The CLI has its own repository on GitLab, `dbvault1/dbvault-cli`, released with the
same version numbers as the server.

## A change, start to finish

1. Branch from `develop`: `feature/DBV-<n>-<short-summary>` (or `fix/…`, `chore/…`).
2. Commit with [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`,
   imperative, at most 72 characters. Add `Refs: DBV-<n>` as a footer when there is an issue.
3. Run `mvn verify` locally. It must be green because the tests ran, not because they skipped.
4. Open a pull request into `develop`. Put the issue key in the title, e.g.
   `feat(target): register and list MySQL targets (DBV-1)`, and say what changed and how to
   check it in the description.
5. CI must pass. Merge with **Create a merge commit**; squash and rebase are disabled so every
   change stays one revertible unit.

A breaking change says so: `!` after the type (`feat(api)!: …`) or a `BREAKING CHANGE:` line in
the description, plus the `breaking-change` label.

## Labels

| Label | Use for |
| --- | --- |
| `enhancement` | New behaviour |
| `bug` | Something that does not work as intended |
| `documentation` | Documentation only |
| `dependencies` | Dependency updates (Dependabot sets this) |
| `breaking-change` | Changes that existing users must act on |
| `security` | Security fixes and hardening |

## Releases

Releases follow GitFlow: a `release/<version>` branch from `develop`, merged into `main` and back
into `develop`, and a `v<version>` tag on `main`. Only maintainers merge release and hotfix
branches and push tags. The steps are in [docs/branching.md](docs/branching.md).
