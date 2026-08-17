/*
 * Copyright 2026 the parqet4j authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ninq.parqet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ninq.parqet.auth.TokenProvider;
import dev.ninq.parqet.error.ParqetNotFoundException;
import dev.ninq.parqet.error.ParqetRateLimitException;
import dev.ninq.parqet.error.ParqetUserGoneException;
import dev.ninq.parqet.model.ActivityType;
import dev.ninq.parqet.model.AssetType;
import dev.ninq.parqet.model.ConnectState;
import dev.ninq.parqet.model.Currency;
import dev.ninq.parqet.model.PermissionAction;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParqetClientTest {

    private StubParqetServer server;
    private ParqetClient client;

    @BeforeEach
    void startServer() {
        server = new StubParqetServer();
        client = ParqetClient.builder()
                .baseUri(server.baseUri())
                .tokens(TokenProvider.of("test-token"))
                .retryPolicy(RetryPolicy.none())
                .build();
    }

    @AfterEach
    void stopServer() {
        client.close();
        server.close();
    }

    @Test
    void readsTheConnectionInfoAndSendsTheBearerToken() {
        server.enqueueOk(
                """
                        {
                          "userId": "u1",
                          "installationId": "i1",
                          "state": "active",
                          "permissions": [
                            {"action": "read", "resourceType": "portfolio", "resourceId": "p1"},
                            {"action": "write", "resourceType": "portfolio", "resourceId": "p1"},
                            {"action": "read", "resourceType": "portfolio", "resourceId": "p2"}
                          ]
                        }
                        """);

        var info = client.user();

        assertThat(info.userId()).isEqualTo("u1");
        assertThat(info.state()).isEqualTo(ConnectState.ACTIVE);
        assertThat(info.isActive()).isTrue();
        assertThat(info.canWrite("p1")).isTrue();
        assertThat(info.canWrite("p2")).isFalse();
        assertThat(info.grantedPortfolioIds()).containsExactly("p1", "p2");
        assertThat(info.permissions().getFirst().action()).isEqualTo(PermissionAction.READ);

        var request = server.lastRequest();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/user");
        assertThat(request.authorization()).isEqualTo("Bearer test-token");
    }

    @Test
    void unwrapsThePortfolioList() {
        server.enqueueOk(
                """
                        {
                          "items": [
                            {
                              "id": "68dbc0b6cf3c111e1be1d411",
                              "currency": "EUR",
                              "name": "Depot",
                              "createdAt": "2025-11-17T09:33:39.892Z",
                              "distinctBrokers": ["trade_republic"],
                              "subAccounts": [
                                {"id": "p1::s1", "isDefault": true},
                                {"id": "p1::s2", "name": "Zweitdepot", "isDefault": false}
                              ]
                            }
                          ]
                        }
                        """);

        var portfolios = client.portfolios().list();

        assertThat(portfolios).hasSize(1);
        var portfolio = portfolios.getFirst();
        assertThat(portfolio.name()).isEqualTo("Depot");
        assertThat(portfolio.currency()).isEqualTo(Currency.EUR);
        assertThat(portfolio.createdAt()).isEqualTo(Instant.parse("2025-11-17T09:33:39.892Z"));
        assertThat(portfolio.distinctBrokers()).containsExactly("trade_republic");
        assertThat(portfolio.defaultSubAccount().id()).isEqualTo("p1::s1");
        assertThat(portfolio.defaultSubAccount().displayName()).isEmpty();
        assertThat(portfolio.subAccounts().get(1).displayName()).contains("Zweitdepot");
        assertThat(portfolio.subAccounts().get(1).portfolioId()).isEqualTo("p1");
        assertThat(server.lastRequest().path()).isEqualTo("/portfolios");
    }

    @Test
    void encodesActivityFiltersAsRepeatedParameters() {
        server.enqueueOk("{\"activities\": [], \"cursor\": null}");

        client.portfolio("p1")
                .activities(ActivityQuery.builder()
                        .limit(250)
                        .types(ActivityType.BUY, ActivityType.SELL)
                        .assetTypes(AssetType.SECURITY)
                        .holdings("h1")
                        .build());

        var request = server.lastRequest();
        assertThat(request.path()).isEqualTo("/portfolios/p1/activities");
        assertThat(request.query())
                .isEqualTo("limit=250&activityType=buy&activityType=sell&assetType=security&holdingId=h1");
    }

    @Test
    void followsTheCursorAcrossPages() {
        server.enqueueOk(activityPage("a1", "cursor-2"));
        server.enqueueOk(activityPage("a2", "cursor-3"));
        server.enqueueOk(activityPage("a3", null));

        var ids = client.portfolio("p1").activityStream(ActivityQuery.all()).map(a -> a.id()).toList();

        assertThat(ids).containsExactly("a1", "a2", "a3");
        assertThat(server.requests()).hasSize(3);
        assertThat(server.requests().get(0).query()).isNull();
        assertThat(server.requests().get(1).query()).isEqualTo("cursor=cursor-2");
        assertThat(server.requests().get(2).query()).isEqualTo("cursor=cursor-3");
    }

    @Test
    void doesNoIoUntilTheStreamIsConsumed() {
        var stream = client.portfolio("p1").activityStream(ActivityQuery.all());

        assertThat(server.requests()).isEmpty();

        server.enqueueOk(activityPage("a1", null));
        assertThat(stream.count()).isEqualTo(1);
    }

    @Test
    void parsesTheDisposalFieldsOnlyOnSells() {
        server.enqueueOk(
                """
                        {
                          "activities": [{
                            "id": "a1", "type": "sell", "holdingId": "h1", "holdingAssetType": "security",
                            "asset": {"assetIdentifierType": "isin", "isin": "US0378331005"},
                            "currency": "EUR", "datetime": "2025-11-17T09:33:39.892Z",
                            "shares": 10, "price": 234.2, "amount": 2342, "amountNet": 2340,
                            "tax": 1.5, "fee": 0.5, "broker": "trade_republic",
                            "realizedGains": 120.5, "realizedGainsNet": 118.5,
                            "buyAmountNet": 2221.5, "avgHoldingPeriod": 412.5,
                            "fx": {
                              "rate": 1.08, "originalCurrency": "USD", "originalPrice": 253.0,
                              "originalTax": 1.62, "originalFee": 0.54, "originalAmount": 2529.4,
                              "originalAmountNet": 2527.2
                            }
                          }],
                          "cursor": null
                        }
                        """);

        var activity = client.portfolio("p1").activities(ActivityQuery.all()).activities().getFirst();

        assertThat(activity.isDisposal()).isTrue();
        assertThat(activity.realizedGainsIfPresent()).map(java.math.BigDecimal::doubleValue).contains(120.5);
        assertThat(activity.avgHoldingPeriodIfPresent()).hasValue(412.5);
        assertThat(activity.fxIfPresent()).isPresent();
        assertThat(activity.fx().originalCurrency()).isEqualTo(Currency.USD);
        assertThat(activity.brokerIfSet()).contains(dev.ninq.parqet.model.Broker.TRADE_REPUBLIC);
        assertThat(activity.externalIdIfSet()).isEmpty();
    }

    @Test
    void mapsStatusCodesToTypedExceptions() {
        server.enqueue(new StubParqetServer.Response(404, "{\"message\":\"Not Found\",\"statusCode\":404}", Map.of()));
        assertThatThrownBy(() -> client.portfolio("nope").holdings())
                .isInstanceOf(ParqetNotFoundException.class)
                .hasMessageContaining("Not Found");

        server.enqueue(new StubParqetServer.Response(410, "{\"message\":\"Gone\",\"statusCode\":410}", Map.of()));
        assertThatThrownBy(() -> client.user()).isInstanceOf(ParqetUserGoneException.class);
    }

    @Test
    void surfacesTheRetryAfterHeaderOnRateLimits() {
        server.enqueue(new StubParqetServer.Response(429, "{\"message\":\"Too Many Requests\"}", Map.of("Retry-After", "7")));

        assertThatThrownBy(() -> client.user())
                .isInstanceOf(ParqetRateLimitException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ParqetRateLimitException.class))
                .satisfies(e -> assertThat(e.retryAfter()).contains(Duration.ofSeconds(7)));
    }

    @Test
    void retriesRateLimitedReadsWhenThePolicyAllowsIt() {
        try (var retrying = ParqetClient.builder()
                .baseUri(server.baseUri())
                .tokens(TokenProvider.of("test-token"))
                .retryPolicy(new RetryPolicy(3, Duration.ofMillis(1), Duration.ofMillis(2), 1.0))
                .build()) {

            server.enqueue(new StubParqetServer.Response(429, "{\"message\":\"slow down\"}", Map.of("Retry-After", "0")));
            server.enqueueOk("{\"items\": []}");

            assertThat(retrying.portfolios().list()).isEmpty();
            assertThat(server.requests()).hasSize(2);
        }
    }

    @Test
    void refreshesTheTokenOnceWhenTheApiRejectsIt() {
        var refreshes = new AtomicInteger();
        var provider = new TokenProvider() {

            @Override
            public String accessToken() {
                return refreshes.get() == 0 ? "stale" : "fresh";
            }

            @Override
            public boolean refresh() {
                refreshes.incrementAndGet();
                return true;
            }
        };

        try (var refreshing = ParqetClient.builder()
                .baseUri(server.baseUri())
                .tokens(provider)
                .retryPolicy(RetryPolicy.none())
                .build()) {

            server.enqueue(new StubParqetServer.Response(401, "{\"message\":\"Unauthorized\"}", Map.of()));
            server.enqueueOk("{\"items\": []}");

            assertThat(refreshing.portfolios().list()).isEmpty();
            assertThat(refreshes).hasValue(1);
            assertThat(server.requests()).hasSize(2);
            assertThat(server.requests().get(0).authorization()).isEqualTo("Bearer stale");
            assertThat(server.requests().get(1).authorization()).isEqualTo("Bearer fresh");
        }
    }

    @Test
    void percentEncodesThePortfolioIdInThePath() {
        server.enqueueOk("{\"items\": []}");

        client.portfolio("a b/c").holdings();

        assertThat(server.lastRequest().path()).isEqualTo("/portfolios/a%20b%2Fc/holdings");
    }

    private static String activityPage(String id, String cursor) {
        return """
                {
                  "activities": [{
                    "id": "%s", "type": "buy", "holdingId": "h1", "holdingAssetType": "security",
                    "asset": {"assetIdentifierType": "isin", "isin": "US0378331005"},
                    "currency": "EUR", "datetime": "2025-11-17T09:33:39.892Z",
                    "shares": 1, "price": 10, "amount": 10, "amountNet": 10, "fx": null
                  }],
                  "cursor": %s
                }
                """
                .formatted(id, cursor == null ? "null" : "\"" + cursor + "\"");
    }
}
