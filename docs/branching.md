# Branching model

GitFlow. Two long-lived branches, three families of short-lived ones.

```
main     ──●───────────────────────●─────────●──▶   only releases, every commit tagged
            \                     /         /
hotfix/*     \                   /       ──●──      from main, back into main and develop
              \                 /       /
release/*      \          ────●─       /            from develop, stabilise, then main
                \        /     \      /
develop  ────────●──●──●────────●────●─────────▶    integration
feature/*      ─●─  ─●─                             from develop, back into develop
```

| Branch | Cut from | Merges into | Lives for |
| --- | --- | --- | --- |
| `main` | — | — | forever; only ever receives merges from `release/*` and `hotfix/*` |
| `develop` | `main` | — | forever; the default branch |
| `feature/*` | `develop` | `develop` | one change |
| `release/*` | `develop` | `main` **and** `develop` | one release |
| `hotfix/*` | `main` | `main` **and** `develop` | one urgent fix |

## Naming

- `feature/DBV-<n>-<short-kebab-summary>`, e.g. `feature/DBV-3-run-logical-backup`. The branch
  name is for people; the Jira key that counts is the one in the pull request title.
- `release/<version>`, e.g. `release/0.1.0`. No `v`.
- `hotfix/<version>`, e.g. `hotfix/0.2.1`: the version being produced, not the one being fixed.
- Tags carry the `v`: `v0.1.0`.

## Never commit directly to `main` or `develop`

Features reach `develop` through a pull request merged with a merge commit. The repository
rules require a pull request and a green `verify` check on both branches, and forbid force-pushes
and deletion. Only repository administrators may bypass them, and only to merge a release or a
hotfix.

## Cutting a release

The version in the parent `pom.xml` is hardcoded, so the release branch is where it changes,
twice.

```bash
git switch develop && git pull
git switch -c release/0.1.0

mvn versions:set -DnewVersion=0.1.0 -DprocessAllModules -DgenerateBackupPoms=false
mvn verify
git commit -am "chore: release 0.1.0"

git switch main
git merge --no-ff release/0.1.0
git tag -a v0.1.0 -m "0.1.0"
git push origin main v0.1.0

# Back into develop, or develop keeps the old version and loses the tag's ancestry.
git switch develop
git merge --no-ff release/0.1.0
mvn versions:set -DnewVersion=0.2.0-SNAPSHOT -DprocessAllModules -DgenerateBackupPoms=false
git commit -am "chore: open 0.2.0-SNAPSHOT"
git push origin develop

git branch -d release/0.1.0
```

Push the tag before the release notes are published: the GitHub Release is created against an
existing tag. Only bug fixes go onto a `release/*` branch.

## Shipping a hotfix

Cut from `main`, because `develop` may already contain unreleased work.

```bash
git switch main && git pull
git switch -c hotfix/0.2.1
# fix, with a test that fails without it; open a pull request into main
mvn versions:set -DnewVersion=0.2.1 -DprocessAllModules -DgenerateBackupPoms=false
git commit -am "chore: release 0.2.1"
git switch main && git merge --no-ff hotfix/0.2.1
git tag -a v0.2.1 -m "0.2.1"
git switch develop && git merge --no-ff hotfix/0.2.1
```

## What CI enforces

`verify` (`mvn verify`) and `workflow lint` (actionlint) run on pushes to `main`, `develop`,
`release/*` and `hotfix/*`, and on every pull request into `main` or `develop`.
