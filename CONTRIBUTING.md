# Contributing to parqet4j

Thanks for looking. This is a small, focused library; the bar is that a change makes the client more
faithful to the Parqet Connect API or easier to use, without growing its dependency list.

## Getting set up

You need JDK 25 and Maven 3.9+. Both are checked by the enforcer plugin, so a wrong version fails
fast rather than mysteriously.

```bash
mvn verify          # every gate: format, -Werror compile, tests, javadoc lint
mvn spotless:apply  # fix formatting — run this before committing
```

## What `mvn verify` checks

| Gate                  | Fails when                                               |
| --------------------- | -------------------------------------------------------- |
| Spotless (`validate`) | formatting or the Apache-2.0 header drifts               |
| `-Xlint:all -Werror`  | any compiler warning                                     |
| Surefire              | a unit test fails                                        |
| Failsafe              | an `*IT` fails (the `live` ones are excluded by default) |
| Javadoc `doclint=all` | a missing or broken doc comment on the published API     |

Markdown is linted with [`rumdl`](https://github.com/rvben/rumdl); run `rumdl fmt` after editing
docs, as CI runs `rumdl check`.

## Conventions

- **Commits follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)**
  (`feat:`, `fix:`, `docs:`, `test:`, `build:`, `ci:`, `chore:`; `!` or `BREAKING CHANGE:` for
  breaking changes). Reference the requirement or task key (`FR-x.y`, `T-xxx`) in the body or scope.
- Update `CHANGELOG.md` under `[Unreleased]` for anything user-visible.
- New behaviour needs a test. Wire-level behaviour needs a stub-server test, not just a unit test.

## When the API changes

`SpecCoverageTest` is the tripwire. When Parqet ships a change, it fails and tells you what moved.

1. Refresh the vendored document:

   ```bash
   curl -sSL https://developer.parqet.com/api-spec/current.json \
     -o src/test/resources/openapi/parqet-connect-0.1.0.json
   ```

   Rename the file if the spec's `info.version` changed, and update the reference in
   `SpecCoverageTest`.
2. Run `mvn test -Dtest=SpecCoverageTest` and work through the failures — a new operation, a new
   property on a schema, or a new enum constant.
3. Add the model or method, then a stub-server test that covers it.

New currencies and brokers only fail this test; they do not break existing users, because both enums
read unknown values back as `UNKNOWN`.

## Testing notes

- Tests talk to `StubParqetServer`, a real socket speaking HTTP, rather than a mocked `HttpClient`.
  That is deliberate: header names, percent-encoding and status handling are where a client actually
  breaks. Keep it built on `java.net` alone — pulling in `com.sun.net.httpserver` would force a JPMS
  readability edge onto every build.
- `ParqetLiveIT` runs against the real API and is excluded unless you opt in:

  ```bash
  PARQET_ACCESS_TOKEN=... mvn verify -Plive
  ```

  It self-skips without a token, and it never writes — a live test that created portfolios would
  leave debris in a real account. Keep it that way.

## Design guidelines

- **No new runtime dependencies.** Two is the budget: `jackson-databind` and `slf4j-api`. If you
  believe a third is warranted, open an issue first — that is a design decision, not a patch.
- **Sealed over `instanceof` chains.** Where the API discriminates on a property, model it as a
  sealed hierarchy so callers get an exhaustive `switch`.
- **Validate early.** A request that the API would reject should fail in the constructor, with a
  message naming the field.
- **Never log a token.** No `Authorization` header, access token or refresh token in any log line,
  exception message or `toString()`.
- **Tolerate additions, refuse guesses.** Unknown JSON properties are ignored. An unknown enum value
  reads as `UNKNOWN` and throws if you try to send it back — silently writing something wrong into
  someone's portfolio is the worse failure.

## Licensing of contributions

parqet4j is licensed under the [Apache License 2.0](LICENSE). By submitting a pull request you agree
that your contribution is licensed under the same terms, per section 5 of that license — inbound is
outbound. There is no CLA to sign.

Keep the Apache-2.0 header on every new source file — `mvn spotless:apply` adds it for you. Only
contribute code you wrote or are otherwise entitled to submit, and do not paste in code under an
incompatible license.

## Reporting a bug

Include the `cf-ray` request id from the exception message if the API was involved; it is what Parqet
support can trace. Never paste an access or refresh token into an issue.

Found a security problem instead? Do not open an issue — see [SECURITY.md](SECURITY.md) for the
private reporting channel.
