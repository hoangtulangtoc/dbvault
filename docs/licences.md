# Third-party licences

The application jar bundles the libraries it runs on. The build lists them, with
the licence each one's own POM declares, in `THIRD-PARTY.txt` at the root of the
jar's classes (`BOOT-INF/classes/THIRD-PARTY.txt`), so the list always matches
the jar it ships in:

```sh
unzip -p web/target/web-*.jar BOOT-INF/classes/THIRD-PARTY.txt
```

It is written by the `license-maven-plugin` (`add-third-party`, during
`generate-resources` of the `web` module) through `web/src/license/THIRD-PARTY.ftl`.

- Runtime and compile dependencies are listed; test and provided ones are not,
  and neither is Lombok, which only runs at compile time and is not packaged.
- dbvault's own modules are left out. Their POMs inherit Spring Boot's
  `<licenses>` from the parent, which is not a licence dbvault has chosen.
- A library whose POM declares no licence fails the build (`failOnMissing`),
  instead of shipping unreported.
- Spellings of the same licence are merged (`licenseMerges` in `web/pom.xml`).
  A library that offers a choice lists every licence it names: Logback, for
  example, is EPL 2.0 or LGPL 2.1, and the Jakarta APIs are EPL 2.0 or GPL 2
  with the Classpath Exception.

The report covers the jar only. The container image also carries the MySQL,
PostgreSQL and MongoDB client tools and the Docker CLI, installed from their
distributions' packages under their own licences (see
[deployment.md](deployment.md#what-the-image-contains)). The fonts the console
serves are covered by `NOTICE`.
