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

import dev.ninq.parqet.auth.TokenProvider;
import dev.ninq.parqet.model.ChartMark;
import dev.ninq.parqet.model.Currency;
import dev.ninq.parqet.model.HoldingAsset;
import dev.ninq.parqet.model.PerformanceRequest;
import dev.ninq.parqet.model.RelativeInterval;
import dev.ninq.parqet.model.Timeframe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PerformanceTest {

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
    void sendsOnlyWhatWasSetAndFlattensTheInIntervalEnvelopes() {
        server.enqueueOk(fixture());

        var result = client.performance(PerformanceRequest.of("p1", "p2")
                .interval(Timeframe.of(RelativeInterval.YEAR_TO_DATE))
                .currency(Currency.EUR));

        assertThat(server.lastRequest().path()).isEqualTo("/performance");
        assertThat(server.lastRequest().body())
                .isEqualTo("{\"portfolioIds\":[\"p1\",\"p2\"],\"interval\":{\"type\":\"relative\",\"value\":\"ytd\"},\"currency\":\"EUR\"}");

        var performance = result.performance();
        assertThat(performance.xirrIfPresent()).hasValue(0.1234);
        assertThat(performance.ttwrorIfPresent()).hasValue(0.0987);
        assertThat(performance.fees()).isEqualByComparingTo("12.5");
        assertThat(performance.taxes()).isEqualByComparingTo("42.75");
        assertThat(performance.unrealizedGains().gainNet()).isEqualByComparingTo("1450.25");
        assertThat(performance.realizedGains().returnGross()).isEqualTo(0.08);
        assertThat(performance.dividendsIfPresent()).isPresent();
        assertThat(performance.dividends().taxes()).isEqualByComparingTo("20.0");
        assertThat(performance.valuationChange()).isEqualByComparingTo("1820.25");
    }

    @Test
    void readsTheHoldingBreakdown() {
        server.enqueueOk(fixture());

        var holding = client.performance(PerformanceRequest.of("p1")).holdings().getFirst();

        assertThat(holding.id()).isEqualTo("h1");
        assertThat(holding.asset()).isEqualTo(new HoldingAsset.Security("US0378331005", "Apple Inc."));
        assertThat(holding.earliestActivityDate()).isEqualTo(LocalDate.of(2024, 3, 11));
        assertThat(holding.startQuote().fx().originalCurrency()).isEqualTo(Currency.USD);
        assertThat(holding.quote().fx()).isNull();
        assertThat(holding.quote().datetime()).isEqualTo(Instant.parse("2026-08-17T00:00:00Z"));
        assertThat(holding.position().unrealizedGain()).isEqualByComparingTo("542.0");
        assertThat(holding.position().isSold()).isFalse();
        // A holding with no dividends in the interval reports null rather than zeroes.
        assertThat(holding.performance().dividendsIfPresent()).isEmpty();
        assertThat(holding.performance().ttwrorIfPresent()).isEmpty();
        assertThat(holding.performance().xirrIfPresent()).hasValue(0.2);
    }

    @Test
    void readsTheAllocationAndTheChartSeries() {
        server.enqueueOk(fixture());

        var result = client.performance(PerformanceRequest.of("p1"));

        assertThat(result.interval().start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.netAllocations().positive().holdings()).containsEntry("h1", new BigDecimal("2342.0"));
        assertThat(result.netAllocations().negative().holdings()).isEmpty();
        assertThat(result.charts()).hasSize(2);
        assertThat(result.charts().getFirst().mark()).isEqualTo(ChartMark.BEGIN_OF_DAY);
        assertThat(result.charts().getLast().mark()).isEqualTo(ChartMark.MOST_RECENT);
        assertThat(result.charts().getLast().values().drawdown()).isEqualTo(-0.0213);
    }

    @Test
    void sendsAnAbsoluteTimeframeAndASubAccountFilter() {
        server.enqueueOk(fixture());

        client.performance(PerformanceRequest.of("p1")
                .interval(Timeframe.between(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-06-30T00:00:00Z")))
                .subAccounts(List.of("p1::s1")));

        assertThat(server.lastRequest().body())
                .contains("\"type\":\"absolute\"")
                .contains("\"start\":\"2026-01-01T00:00:00Z\"")
                .contains("\"end\":\"2026-06-30T00:00:00Z\"")
                .contains("\"filter\":{\"subAccountIds\":[\"p1::s1\"]}");
    }

    private static String fixture() {
        try (var in = PerformanceTest.class.getResourceAsStream("/fixtures/performance.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
