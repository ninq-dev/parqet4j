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
import dev.ninq.parqet.model.ActivityType;
import dev.ninq.parqet.model.AssetProduct;
import dev.ninq.parqet.model.AssetType;
import dev.ninq.parqet.model.Broker;
import dev.ninq.parqet.model.CommodityIdentifier;
import dev.ninq.parqet.model.CommodityUnit;
import dev.ninq.parqet.model.Currency;
import dev.ninq.parqet.model.NewActivity;
import dev.ninq.parqet.model.NewHolding;
import dev.ninq.parqet.model.Quote;
import dev.ninq.parqet.model.QuoteUpdate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortfolioWriteTest {

    private static final Instant WHEN = Instant.parse("2025-11-17T09:33:39.892Z");

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
    void createsAPortfolio() {
        server.enqueueOk("{\"id\": \"p-new\"}");

        var id = client.portfolios().create("Depot");

        assertThat(id).isEqualTo("p-new");
        assertThat(server.lastRequest().method()).isEqualTo("POST");
        assertThat(server.lastRequest().path()).isEqualTo("/portfolios");
        assertThat(server.lastRequest().body()).isEqualTo("{\"name\":\"Depot\"}");
    }

    @Test
    void sendsASecurityActivityWithItsDiscriminatorAndWithoutUnsetFields() {
        server.enqueueOk("{\"createdActivities\": [{\"_id\": \"a1\"}], \"notCreatedActivities\": []}");

        var result = client.portfolio("p1")
                .createActivity(NewActivity.security(ActivityType.BUY, "US0378331005")
                        .shares(new BigDecimal("10"))
                        .price(new BigDecimal("234.20"))
                        .currency(Currency.USD)
                        .datetime(WHEN)
                        .fee(new BigDecimal("1.00"))
                        .broker(Broker.TRADE_REPUBLIC)
                        .externalId("sync-1")
                        .build());

        assertThat(result.createdIds()).containsExactly("a1");
        assertThat(result.isCompletelyAccepted()).isTrue();

        var body = server.lastRequest().body();
        assertThat(server.lastRequest().path()).isEqualTo("/portfolios/p1/activities");
        assertThat(body)
                .contains("\"assetIdentifierType\":\"isin\"")
                .contains("\"isin\":\"US0378331005\"")
                .contains("\"shares\":10")
                .contains("\"price\":234.20")
                .contains("\"currency\":\"USD\"")
                .contains("\"datetime\":\"2025-11-17T09:33:39.892Z\"")
                .contains("\"broker\":\"trade_republic\"")
                .contains("\"externalId\":\"sync-1\"")
                .doesNotContain("\"tax\"")
                .doesNotContain("\"description\"")
                .doesNotContain("null");
    }

    @Test
    void sendsACashActivityWithAnAmountInsteadOfShares() {
        server.enqueueOk("{\"createdActivities\": [{\"_id\": \"a2\"}]}");

        client.portfolio("p1")
                .createActivity(NewActivity.cash(ActivityType.DEPOSIT, "h-cash")
                        .amount(new BigDecimal("2342"))
                        .currency(Currency.EUR)
                        .datetime(WHEN)
                        .build());

        assertThat(server.lastRequest().body())
                .contains("\"assetIdentifierType\":\"cash\"")
                .contains("\"holding_id\":\"h-cash\"")
                .contains("\"amount\":2342")
                .doesNotContain("\"shares\"");
    }

    @Test
    void reportsPartiallyRejectedBatches() {
        server.enqueueOk(
                """
                        {
                          "createdActivities": [{"_id": "a1"}],
                          "notCreatedActivities": [
                            {"error": {"originalIndex": 1, "code": "UNKNOWN_ISIN", "message": "no such security"}}
                          ]
                        }
                        """);

        var result = client.portfolio("p1")
                .createActivities(List.of(
                        NewActivity.security(ActivityType.BUY, "US0378331005")
                                .shares(BigDecimal.ONE)
                                .price(BigDecimal.TEN)
                                .currency(Currency.EUR)
                                .datetime(WHEN)
                                .build(),
                        NewActivity.security(ActivityType.BUY, "XX0000000009")
                                .shares(BigDecimal.ONE)
                                .price(BigDecimal.TEN)
                                .currency(Currency.EUR)
                                .datetime(WHEN)
                                .build()));

        assertThat(result.isCompletelyAccepted()).isFalse();
        assertThat(result.createdIds()).containsExactly("a1");
        assertThat(result.rejected()).singleElement().satisfies(rejection -> {
            assertThat(rejection.originalIndex()).isEqualTo(1);
            assertThat(rejection.code()).isEqualTo("UNKNOWN_ISIN");
        });
    }

    @Test
    void routesEachHoldingKindToItsOwnEndpoint() {
        assertThat(pathForCreating(NewHolding.cash("Cash").build())).isEqualTo("/portfolios/p1/holdings/cash");
        assertThat(pathForCreating(NewHolding.commodity("Gold", CommodityIdentifier.GOLD)
                .definition(BigDecimal.ONE, new BigDecimal("999.9"), CommodityUnit.TROY_OUNCE)
                .build()))
                .isEqualTo("/portfolios/p1/holdings/commodity");
        assertThat(pathForCreating(NewHolding.custom("Art", AssetProduct.MATERIAL_ASSET).build()))
                .isEqualTo("/portfolios/p1/holdings/custom");
        assertThat(pathForCreating(NewHolding.realEstate("Haus").build())).isEqualTo("/portfolios/p1/holdings/real-estate");
        assertThat(pathForCreating(NewHolding.insurance("Police").build())).isEqualTo("/portfolios/p1/holdings/insurance");
        assertThat(pathForCreating(NewHolding.p2p("Bondora").build())).isEqualTo("/portfolios/p1/holdings/p2p");
    }

    @Test
    void sendsACashHoldingWithItsReferenceAccountClasses() {
        server.enqueueOk("{\"id\": \"h-new\"}");

        var id = client.portfolio("p1")
                .createHolding(NewHolding.cash("Verrechnungskonto")
                        .currency(Currency.EUR)
                        .referenceAccountFor(AssetType.SECURITY, AssetType.CRYPTO)
                        .externalId("broker-cash-1")
                        .build());

        assertThat(id).isEqualTo("h-new");
        assertThat(server.lastRequest().body())
                .contains("\"name\":\"Verrechnungskonto\"")
                .contains("\"currency\":\"EUR\"")
                .contains("\"referenceAccountFor\":[\"security\",\"crypto\"]")
                .contains("\"externalId\":\"broker-cash-1\"")
                .doesNotContain("assetType");
    }

    @Test
    void pushesQuotesAddressedByExternalId() {
        server.enqueueOk("{}");

        client.portfolio("p1")
                .pushQuotes(QuoteUpdate.forExternalId(
                        "my-asset", List.of(new Quote(Currency.EUR, WHEN, new BigDecimal("1234.56")))));

        assertThat(server.lastRequest().path()).isEqualTo("/portfolios/p1/quotes/user-managed");
        assertThat(server.lastRequest().body())
                .contains("\"identifier\":{\"type\":\"externalId\",\"value\":\"my-asset\"}")
                .contains("\"price\":1234.56");
    }

    @Test
    void rejectsIllegalRequestsBeforeAnyIo() {
        assertThatThrownBy(() -> NewActivity.security(ActivityType.BUY, "not-an-isin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isin must match");

        assertThatThrownBy(() -> NewActivity.p2p(ActivityType.INTEREST, "h1")
                .amount(BigDecimal.ONE)
                .currency(Currency.EUR)
                .datetime(WHEN)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("use dividend for payouts");

        assertThatThrownBy(() -> NewHolding.cash("x".repeat(81)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 80 characters");

        assertThatThrownBy(() -> NewHolding.cash("Cash").externalId("has spaces").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalId must match");

        assertThat(server.requests()).isEmpty();
    }

    private String pathForCreating(NewHolding holding) {
        server.enqueueOk("{\"id\": \"h\"}");
        client.portfolio("p1").createHolding(holding);
        return server.lastRequest().path();
    }
}
