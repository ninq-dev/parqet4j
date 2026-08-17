# Security policy

## Reporting a vulnerability

Report privately through GitHub's
[private vulnerability reporting](https://github.com/ninq-dev/parqet4j/security/advisories/new) —
**Security → Report a vulnerability** on the repository. That channel is visible only to the
maintainers until an advisory is published.

Please do not open a public issue for a suspected vulnerability, and do not include an access token,
refresh token, client secret or authorization code in the report. If you need to show a request or
response, redact the `Authorization` header and quote the `cf-ray` id instead — it identifies the
request without carrying a credential.

Useful to include:

- the version of parqet4j and the JDK you are on
- what an attacker could do, and what access they would need to do it
- a minimal reproduction, ideally against `StubParqetServer` rather than a real account

You can expect an acknowledgement within a few days. This is a small project maintained in spare
time, so a fix may take longer than that; you will be told where it stands rather than left waiting.

## Supported versions

parqet4j has not had a release yet. Once `0.1.0` is published, security fixes land on the most recent
minor line, and older lines are not patched. While the version stays below `1.0.0`, that means the
current minor only.

## What counts

This library holds OAuth 2.0 credentials on behalf of a Parqet user, so the things most worth
reporting are:

- **Credential leaks.** An access token, refresh token, client secret or authorization code that
  reaches a log line, an exception message, a `toString()` or a stack trace. Never logging a token is
  a stated invariant of this codebase; a violation of it is a vulnerability, not a bug.
- **Flaws in the authorization-code flow.** A weakness in PKCE verifier generation, the CSRF `state`
  check, or the redirect-URI handling in `ParqetOAuth`.
- **Credential handling across refresh.** A rotated refresh token that is dropped, reused, or handed
  to the wrong callback in `RefreshingTokenProvider`.
- **Request forgery through untrusted input.** A portfolio id, external id or filter value that can
  break out of a path segment or query string and reach an endpoint the caller did not intend.
- **Deserialization or resource-exhaustion issues** reachable from an API response.

Out of scope, because they are not parqet4j's to fix: vulnerabilities in the Parqet Connect API
itself (report those to Parqet), in `jackson-databind` or `slf4j-api` upstream, and anything that
requires an attacker to already control the machine running your code.

If you are unsure whether something qualifies, report it anyway.
