# parqet4j

A lightweight Java client for the [Parqet Connect API](https://developer.parqet.com/docs/api) — typed
models, simple access to portfolios, holdings and transactions.

[![CI](https://github.com/ninq-dev/parqet4j/actions/workflows/ci.yml/badge.svg)](https://github.com/ninq-dev/parqet4j/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

- **Complete.** Every one of the API's 14 operations, reads and writes alike.
- **Typed.** A record per shape, sealed hierarchies where the API discriminates, and validation that
  rejects a malformed request before it reaches the wire.
- **Small.** Two runtime dependencies — `jackson-databind` and `slf4j-api`. HTTP comes from the JDK.
- **Honest about drift.** The OpenAPI document is vendored and asserted against on every build, so a
  new endpoint, property or enum value fails a test instead of surprising you at runtime.

**Using it:** [Install](#install) · [Quick start](#quick-start) · [Authentication](#authentication) ·
[Reading](#reading) · [Writing](#writing) · [Performance](#performance) · [Errors](#errors) ·
[Configuration](#configuration)

**Working on it:** [Developing parqet4j](#developing-parqet4j) — prerequisites, first build, project
layout, running the examples against a real account

## Requirements

JDK 25 or newer. The artifact is a JPMS module, `dev.ninq.parqet`.

## Install

```xml
<dependency>
    <groupId>dev.ninq</groupId>
    <artifactId>parqet4j</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Quick start

```java
import dev.ninq.parqet.ParqetClient;
import dev.ninq.parqet.auth.TokenProvider;

try (var parqet = ParqetClient.builder()
        .tokens(TokenProvider.of(accessToken))
        .build()) {

    for (var portfolio : parqet.portfolios().list()) {
        var holdings = parqet.portfolio(portfolio.id()).holdings();
        System.out.printf("%s (%s): %d holdings%n", portfolio.name(), portfolio.currency(), holdings.size());
    }
}
```

## Authentication

Parqet Connect uses OAuth 2.0 **authorization code with PKCE**. Register an integration in the
[Developer Console](https://developer.parqet.com) to get a Client ID and register your redirect URLs.
parqet4j runs both server-side halves of the flow; your application only has to serve the redirect
URI and keep a session.

```text
User                    Your app                         Parqet
  │  "Connect Parqet"       │                               │
  │────────────────────────▶│                               │
  │                         │ authorizationRequest()        │
  │                         │──── redirect (PKCE + state) ─▶│
  │◀───────────────────── login & consent ──────────────────│
  │──────────────────────── approve ───────────────────────▶│
  │                         │◀──── callback: code + state ──│
  │                         │ exchangeCode() ──────────────▶│
  │                         │◀──── access + refresh token ──│
```

Everything below is derived from the live authorization-server metadata; you never have to configure
the endpoints yourself:

|                        |                                                                      |
| ---------------------- | -------------------------------------------------------------------- |
| Issuer                 | `https://connect.parqet.com`                                         |
| Authorization endpoint | `https://connect.parqet.com/oauth2/authorize`                        |
| Token endpoint         | `https://connect.parqet.com/oauth2/token`                            |
| Grants                 | `authorization_code`, `refresh_token`                                |
| PKCE                   | `S256` (required)                                                    |
| Client authentication  | `none` (public clients), `client_secret_basic`, `client_secret_post` |

### Scopes

| Scope             | Constant                | Grants                                                               |
| ----------------- | ----------------------- | -------------------------------------------------------------------- |
| `portfolio:read`  | `Scope.PORTFOLIO_READ`  | Every read: user info, portfolios, holdings, activities, performance |
| `portfolio:write` | `Scope.PORTFOLIO_WRITE` | Creating portfolios, holdings, activities, and pushing quotes        |

The user picks which portfolios to share during consent, so a granted scope is not blanket access —
`parqet.user()` reports what the token actually covers, and `ConnectInfo.canWrite(portfolioId)`
answers the question you usually care about.

### Running the flow

```java
var oauth = ParqetOAuth.builder(clientId)
        .redirectUri(URI.create("https://example.com/callback"))
        // .clientSecret(secret)   // only for confidential clients; PKCE alone is fine otherwise
        .build();

// 1. Before redirecting. The request holds the PKCE verifier and the CSRF state, so it has to
//    survive until the callback — put it in the session, not in a local variable.
var request = oauth.authorizationRequest(Set.of(Scope.PORTFOLIO_READ, Scope.PORTFOLIO_WRITE));
session.put("parqet.oauth", request);
redirect(request.authorizationUri());

// 2. In the callback handler. exchangeCode verifies `state` in constant time and refuses a callback
//    that does not belong to this flow.
var tokens = oauth.exchangeCode(session.remove("parqet.oauth"), code, state);
persist(tokens);   // the refresh token is what keeps the integration working
```

### Keeping the grant alive

Access tokens expire. `TokenProvider.refreshing` renews one 60 seconds before it does, and once more
if the API rejects it anyway. Every renewed set of tokens goes to your callback *before* it is used,
which is where you persist the rotated refresh token.

```java
var provider = TokenProvider.refreshing(oauth, loadTokens(), this::persist);

try (var parqet = ParqetClient.builder().tokens(provider).build()) {
    // …
}
```

Refreshes are serialised behind a `ReentrantLock`, so concurrent callers trigger one exchange, not
several.

### Bringing your own OAuth

If OAuth already lives elsewhere in your stack, skip `ParqetOAuth` entirely:

```java
ParqetClient.builder().tokens(TokenProvider.of(accessToken)).build();
```

`TokenProvider` is an interface — implement `accessToken()` and, optionally, `refresh()` to hook the
client into whatever token store you already have.

### Handling a revoked grant

`ParqetAuthException` (401/403) means the token is gone or lacks the scope. `ParqetUserGoneException`
(410) means the user deleted their Connect account: that is terminal, so drop the stored tokens and
stop syncing rather than retrying.

## Reading

```java
var portfolio = parqet.portfolio(portfolioId);

// Holdings — match on the sealed asset hierarchy to reach the identifying detail.
for (var holding : portfolio.holdings()) {
    var label = switch (holding.asset()) {
        case HoldingAsset.Security s -> s.isin();
        case HoldingAsset.Crypto c -> c.symbol();
        case HoldingAsset.Commodity c -> c.identifier().id();
        default -> holding.assetType().id();
    };
    System.out.println(label);
}

// Activities — activityStream follows the cursor for you, lazily.
var dividends = portfolio.activityStream(ActivityQuery.builder()
                .types(ActivityType.DIVIDEND)
                .assetTypes(AssetType.SECURITY)
                .limit(500)
                .build())
        .toList();

// Or hold the cursor yourself, to resume a sync across restarts.
var page = portfolio.activities(ActivityQuery.builder().cursor(savedCursor).build());
save(page.cursor());
```

## Writing

Writes need the `portfolio:write` scope. Set an `externalId` on anything you create — it is what lets
a later sync recognise what it already sent.

```java
var result = portfolio.createActivities(List.of(
        NewActivity.security(ActivityType.BUY, "US0378331005")
                .shares(new BigDecimal("10"))
                .price(new BigDecimal("234.20"))
                .currency(Currency.USD)
                .datetime(Instant.parse("2025-11-17T09:33:39.892Z"))
                .fee(new BigDecimal("1.00"))
                .broker(Broker.TRADE_REPUBLIC)
                .externalId("sync-000123")
                .build()));

// The API accepts a batch partially — a 2xx does not mean everything landed.
result.rejected().forEach(r -> log.warn("activity {} rejected: {}", r.originalIndex(), r.message()));
```

Holdings work the same way. One `createHolding` call covers all six kinds; the variant you pass picks
the endpoint. Securities and crypto need no explicit holding — booking an activity against an ISIN or
ticker creates one.

```java
var cashId = portfolio.createHolding(NewHolding.cash("Verrechnungskonto")
        .currency(Currency.EUR)
        .referenceAccountFor(AssetType.SECURITY, AssetType.CRYPTO)
        .externalId("broker-cash-1")
        .build());

var artId = portfolio.createHolding(NewHolding.custom("Kandinsky", AssetProduct.MATERIAL_ASSET)
        .externalId("art-1")
        .build());

// Custom assets have no market price, so you supply one.
portfolio.pushQuotes(QuoteUpdate.forExternalId("art-1",
        List.of(new Quote(Currency.EUR, Instant.now(), new BigDecimal("42000")))));
```

## Performance

`POST /performance` can span several portfolios at once, which is how a combined view is produced.

```java
var result = parqet.performance(PerformanceRequest.of(portfolioId, otherPortfolioId)
        .interval(Timeframe.of(RelativeInterval.YEAR_TO_DATE))
        .currency(Currency.EUR));

var kpis = result.performance();
System.out.printf("TTWROR %.2f%%, XIRR %.2f%%, valuation %s -> %s%n",
        kpis.ttwrorIfPresent().orElse(0) * 100,
        kpis.xirrIfPresent().orElse(0) * 100,
        kpis.valuationAtIntervalStart(),
        kpis.valuationAtIntervalEnd());
```

The API nests every figure under an `inInterval` envelope; parqet4j flattens that away.
`result.holdings()` carries the same shape per position, and `result.charts()` is the time series.

## Errors

Everything throws unchecked, rooted at `ParqetException`:

| Exception                  | When                                                       |
| -------------------------- | ---------------------------------------------------------- |
| `ParqetAuthException`      | 401 or 403 — token missing, expired, or lacking the scope  |
| `ParqetNotFoundException`  | 404 — no such portfolio or holding, or it was not shared   |
| `ParqetUserGoneException`  | 410 — the user deleted their Connect account; stop syncing |
| `ParqetRateLimitException` | 429 after the retries ran out; carries `Retry-After`       |
| `ParqetServerException`    | 5xx after the retries ran out                              |
| `ParqetTransportException` | no response at all: I/O, TLS, timeout                      |
| `ParqetProtocolException`  | a response that could not be understood                    |

Reads are retried on 429 and 5xx; writes only on 429, because a rate-limited request never reached
the booking logic while a 5xx may have. Tune or disable it with `RetryPolicy`.

## Configuration

```java
ParqetClient.builder()
        .tokens(provider)
        .userAgent("acme-sync/1.4")            // helps Parqet support trace your calls
        .requestTimeout(Duration.ofSeconds(60))
        .retryPolicy(RetryPolicy.none())
        .httpClient(myHttpClient)              // you keep ownership; close() will not touch it
        .build();
```

Logging goes through SLF4J under `dev.ninq.parqet.*` and is silent by default; enable `DEBUG` to see
request lines, status codes and retry decisions. Tokens and `Authorization` headers are never logged,
and `Tokens.toString()` is redacted.

## Threading

A `ParqetClient` is immutable and safe to share. Calls block, so they suit virtual threads: nothing
in the library holds a monitor across I/O.

## Developing parqet4j

This section is for working **on** the library. To just use it, everything above is enough.

### Prerequisites

| Tool    | Version      | Notes                                                                              |
| ------- | ------------ | ---------------------------------------------------------------------------------- |
| JDK     | 25 or newer  | Any distribution. CI builds on Temurin and GraalVM CE; enforcer checks the version |
| Maven   | 3.9 or newer | Checked by the enforcer plugin                                                     |
| `rumdl` | any          | Optional, for markdown lint. CI runs it; `brew install rumdl` or `uvx rumdl`       |

No Docker, no local server, no network beyond the initial dependency download: the tests bring their
own HTTP server up on a loopback port.

### First build

```bash
git clone https://github.com/ninq-dev/parqet4j.git
cd parqet4j
mvn verify
```

That should end in `BUILD SUCCESS` with 62 tests passing. If it does not, the first thing to check is
`java -version` — the enforcer fails fast on JDK 24 and below, and Maven may be running on a
different JDK than your shell (`mvn -version` prints the one that counts).

### What `mvn verify` checks

Everything is a gate; there is no "warnings are fine" mode.

| Phase              | Gate                  | Fails when                                                                                      |
| ------------------ | --------------------- | ----------------------------------------------------------------------------------------------- |
| `validate`         | Spotless              | formatting or the Apache-2.0 header drifts from `style.xml` / `build-config/license-header.txt` |
| `compile`          | `-Xlint:all -Werror`  | any compiler warning at all                                                                     |
| `test`             | Surefire              | a unit test fails                                                                               |
| `integration-test` | Failsafe              | an `*IT` fails (the `live` ones are excluded unless you opt in)                                 |
| `verify`           | Javadoc `doclint=all` | a missing or broken doc comment on the published API                                            |

The formatting gate is the one that trips people up. Run `mvn spotless:apply` before committing — the
Eclipse profile in `style.xml` wraps at 160 columns and reflows javadoc, so hand-formatting is wasted
effort.

### Project layout

```text
src/main/java/
  module-info.java                 JPMS module dev.ninq.parqet
  dev/ninq/parqet/                 ParqetClient, Portfolios, PortfolioResource,
                                   ActivityQuery, RetryPolicy
  dev/ninq/parqet/auth/            ParqetOAuth, Tokens, TokenProvider, Scope
  dev/ninq/parqet/model/           one record per API shape, plus the enums
  dev/ninq/parqet/error/           the ParqetException hierarchy
  dev/ninq/parqet/internal/        ParqetJson, HttpTransport, Validate, Envelopes
                                   — not exported, no compatibility promise

src/test/java/                     stub-server tests + SpecCoverageTest
src/test/resources/openapi/        the vendored OpenAPI document
examples/                          runnable single-file programs
```

Only the first four packages are exported. Anything in `internal` can change in a patch release.

### Everyday commands

```bash
mvn verify                                   # everything
mvn test                                     # unit tests only, ~2s
mvn test -Dtest=SpecCoverageTest             # check the models against the vendored spec
mvn test -Dtest=ParqetClientTest#followsTheCursorAcrossPages
mvn spotless:apply                           # fix formatting
rumdl fmt .                                  # fix markdown
mvn javadoc:javadoc-no-fork                  # render the API docs to target/reports/apidocs
```

Tests talk to `StubParqetServer`, a real socket speaking HTTP, rather than a mocked `HttpClient`.
That is deliberate: header names, percent-encoding and status handling are where a client actually
breaks. Adding a test usually means queueing a canned response and asserting on what was sent:

```java
server.enqueueOk("{\"id\": \"p-new\"}");

var id = client.portfolios().create("Depot");

assertThat(id).isEqualTo("p-new");
assertThat(server.lastRequest().body()).isEqualTo("{\"name\":\"Depot\"}");
```

### Trying it against a real account

The examples are the fastest way to exercise the client end to end. They live in `examples/` and run
through the JDK's multi-file source launcher — no separate build.

**1. Register an integration.** Sign in to the [Developer Console](https://developer.parqet.com), go
to Integrations, create one, pick the scopes you want, and add `http://localhost:1337/callback` as a
redirect URL. New integrations are private, so only your own Parqet account can authorize them —
which is exactly what you want for development.

**2. Get a token.** `Authorize` runs the whole PKCE flow, including a throwaway callback listener on
port 1337:

```bash
examples/run.sh Authorize <your-client-id>            # portfolio:read
examples/run.sh Authorize <your-client-id> --write    # also portfolio:write
```

It prints the authorization URL, waits for the callback, exchanges the code, and prints the tokens as
`export` lines.

**3. Explore.**

```bash
export PARQET_ACCESS_TOKEN=...

examples/run.sh ListPortfolios                        # user info, portfolios, holdings
examples/run.sh Activities <portfolio-id>             # stream every activity
examples/run.sh Activities <portfolio-id> dividend    # …filtered by type
examples/run.sh Performance <portfolio-id> --interval 1y
examples/run.sh Performance <portfolio-id> <other-id> # combined across portfolios
examples/run.sh SyncActivities <portfolio-id>         # writes! see examples/README.md
```

**4. Run the live smoke tests.** `ParqetLiveIT` is read-only and excluded from normal builds:

```bash
PARQET_ACCESS_TOKEN=... mvn verify -Plive
```

It self-skips without a token, and it never writes — a live test that created portfolios would leave
debris in a real account.

### Seeing what goes over the wire

Logging is SLF4J under `dev.ninq.parqet.*` and silent by default. Put a binding on the classpath and
turn on `DEBUG` to see request lines, status codes, retry decisions and token refreshes. `run.sh`
caches the dependency classpath in `target/cp.txt`, so run any example once first (`mvn clean`
removes it):

```bash
examples/run.sh ListPortfolios                                  # populates target/cp.txt
mvn -q dependency:get -Dartifact=org.slf4j:slf4j-simple:2.0.18
SIMPLE=$(find ~/.m2/repository/org/slf4j/slf4j-simple -name 'slf4j-simple-2.0.18.jar' | head -1)

java -cp "target/classes:$(cat target/cp.txt):$SIMPLE" \
     -Dorg.slf4j.simpleLogger.defaultLogLevel=debug \
     examples/ListPortfolios.java
```

Tokens and `Authorization` headers are never logged, at any level. Keep it that way.

### When the Parqet API changes

`SpecCoverageTest` is the tripwire — it asserts operation coverage, per-schema property binding, and
exact membership of all 47 currencies and 60 brokers against the vendored OpenAPI document.

```bash
curl -sSL https://developer.parqet.com/api-spec/current.json \
  -o src/test/resources/openapi/parqet-connect-0.1.0.json
mvn test -Dtest=SpecCoverageTest
```

The failures name what moved: a new operation, a new property, or a new enum constant. Add the model
or method, then a stub-server test for it. New currencies and brokers only fail this test — they do
not break existing users, because both enums read unknown values back as `UNKNOWN`.

### Releasing

Push a `v*` tag (or run the Release workflow manually). It runs the full lifecycle, GPG-signs the
sources and javadoc JARs, and stages the bundle on the Sonatype Central portal under the `dev.ninq`
namespace; publishing is a manual confirmation there. The required secrets are listed at the top of
`.github/workflows/release.yml`.

### Where to look next

- [CONTRIBUTING.md](CONTRIBUTING.md) — commit conventions, design guidelines, the dependency budget
- [SECURITY.md](SECURITY.md) — how to report a vulnerability privately

## License

Apache-2.0. See [LICENSE](LICENSE).

parqet4j is an independent open-source project and is not affiliated with or endorsed by Parqet
Fintech GmbH.
