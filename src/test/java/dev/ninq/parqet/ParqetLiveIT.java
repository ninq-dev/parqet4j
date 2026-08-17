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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.ninq.parqet.auth.TokenProvider;
import dev.ninq.parqet.model.ConnectState;
import dev.ninq.parqet.model.PerformanceRequest;
import dev.ninq.parqet.model.RelativeInterval;
import dev.ninq.parqet.model.Timeframe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Read-only smoke tests against the real Parqet Connect API.
 * <p>
 * Excluded from every normal build. Run them with an access token to confirm that a stub-server assumption still matches production:
 *
 * <pre>{@code
 * PARQET_ACCESS_TOKEN=... mvn verify -Plive
 * }</pre>
 * <p>
 * They never write: a live test that created portfolios or booked activities would leave debris in a real user's account.
 */
@Tag("live")
class ParqetLiveIT {

    private static ParqetClient client;

    @BeforeAll
    static void connect() {
        var token = System.getenv("PARQET_ACCESS_TOKEN");
        assumeTrue(token != null && !token.isBlank(), "PARQET_ACCESS_TOKEN is not set");
        client = ParqetClient.builder().tokens(TokenProvider.of(token)).userAgent("parqet4j-live-it").build();
    }

    @AfterAll
    static void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void readsTheConnectionInfo() {
        var info = client.user();

        assertThat(info.userId()).isNotBlank();
        assertThat(info.installationId()).isNotBlank();
        assertThat(info.state()).isEqualTo(ConnectState.ACTIVE);
    }

    @Test
    void readsEveryGrantedPortfolioWithItsHoldingsAndActivities() {
        var portfolios = client.portfolios().list();
        assumeTrue(!portfolios.isEmpty(), "the token grants access to no portfolio");

        for (var portfolio : portfolios) {
            assertThat(portfolio.id()).isNotBlank();
            assertThat(portfolio.subAccounts()).anyMatch(sub -> sub.isDefault());

            var resource = client.portfolio(portfolio.id());
            assertThat(resource.holdings()).allSatisfy(holding -> assertThat(holding.asset()).isNotNull());

            // Deserializing the first page exercises every activity shape the account happens to hold.
            var page = resource.activities(ActivityQuery.builder().limit(50).build());
            assertThat(page.activities()).allSatisfy(activity -> assertThat(activity.type()).isNotNull());
        }
    }

    @Test
    void computesYearToDatePerformance() {
        var portfolios = client.portfolios().list();
        assumeTrue(!portfolios.isEmpty(), "the token grants access to no portfolio");

        var result = client.performance(PerformanceRequest.of(portfolios.getFirst().id())
                .interval(Timeframe.of(RelativeInterval.YEAR_TO_DATE)));

        assertThat(result.interval().start()).isNotNull();
        assertThat(result.performance()).isNotNull();
        assertThat(result.charts()).isNotEmpty();
    }
}
