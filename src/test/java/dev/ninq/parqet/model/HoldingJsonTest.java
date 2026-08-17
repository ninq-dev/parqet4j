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
package dev.ninq.parqet.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ninq.parqet.internal.ParqetJson;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HoldingJsonTest {

    @Test
    void readsSecurityHolding() {
        var json = """
                {
                  "id": "68dbc0b6cf3c111e1be1d411",
                  "activityCount": 12,
                  "logo": "https://img.parqet.com/logo/apple.png",
                  "nickname": "Apple",
                  "asset": {"type": "security", "isin": "US0378331005", "name": "Apple Inc."},
                  "externalId": "broker-42",
                  "subAccount": "67910d3879bf5f007acd4164::56910a3879bf5f007acd4539",
                  "currency": "USD"
                }
                """;

        var holding = ParqetJson.read(json, Holding.class);

        assertThat(holding.id()).isEqualTo("68dbc0b6cf3c111e1be1d411");
        assertThat(holding.activityCount()).isEqualTo(12);
        assertThat(holding.currency()).isEqualTo(Currency.USD);
        assertThat(holding.assetType()).isEqualTo(AssetType.SECURITY);
        assertThat(holding.asset()).isEqualTo(new HoldingAsset.Security("US0378331005", "Apple Inc."));
        assertThat(holding.logoUri()).map(Object::toString).contains("https://img.parqet.com/logo/apple.png");
        assertThat(holding.externalIdIfSet()).contains("broker-42");
    }

    @Test
    void readsCommodityHolding() {
        var json = """
                {
                  "id": "h1",
                  "activityCount": 1,
                  "logo": null,
                  "nickname": null,
                  "asset": {
                    "type": "commodity",
                    "identifier": "gold",
                    "name": "Gold",
                    "unit": "oz.tr.",
                    "amount": 1,
                    "purity": 999.9
                  },
                  "subAccount": "p1::s1",
                  "currency": "EUR"
                }
                """;

        var asset = (HoldingAsset.Commodity) ParqetJson.read(json, Holding.class).asset();

        assertThat(asset.identifier()).isEqualTo(CommodityIdentifier.GOLD);
        assertThat(asset.unit()).isEqualTo(CommodityUnit.TROY_OUNCE);
        assertThat(asset.purity()).isEqualByComparingTo(new BigDecimal("999.9"));
    }

    @Test
    void readsMarkerAssetsWithNoPayload() {
        var json = """
                {
                  "id": "h2",
                  "activityCount": 0,
                  "logo": null,
                  "nickname": null,
                  "asset": {"type": "cash"},
                  "subAccount": "p1::s1",
                  "currency": "EUR"
                }
                """;

        var holding = ParqetJson.read(json, Holding.class);

        assertThat(holding.asset()).isEqualTo(new HoldingAsset.Cash());
        assertThat(holding.assetType()).isEqualTo(AssetType.CASH);
        assertThat(holding.nicknameIfSet()).isEmpty();
        assertThat(holding.logoUri()).isEmpty();
    }

    @Test
    void writesTheDiscriminatorBack() {
        var json = ParqetJson.write(new HoldingAsset.Crypto("BTC", "Bitcoin"));

        assertThat(json).isEqualTo("{\"type\":\"crypto\",\"symbol\":\"BTC\",\"name\":\"Bitcoin\"}");
    }

    @Test
    void ignoresPropertiesAddedByTheServer() {
        var json = """
                {
                  "id": "h3",
                  "activityCount": 0,
                  "asset": {"type": "p2p", "somethingNew": 1},
                  "subAccount": "p1::s1",
                  "currency": "EUR",
                  "aFieldFromTheFuture": {"nested": true}
                }
                """;

        assertThat(ParqetJson.read(json, Holding.class).asset()).isEqualTo(new HoldingAsset.P2p());
    }

    @Test
    void rejectsAMalformedIsin() {
        var json = """
                {
                  "id": "h4",
                  "activityCount": 0,
                  "asset": {"type": "security", "isin": "NOPE", "name": "x"},
                  "subAccount": "p1::s1",
                  "currency": "EUR"
                }
                """;

        assertThatThrownBy(() -> ParqetJson.read(json, Holding.class)).hasMessageContaining("Holding");
    }

    @Test
    void mapsAnUnknownCurrencyToUnknownRatherThanFailing() {
        var json = """
                {
                  "id": "h5",
                  "activityCount": 0,
                  "asset": {"type": "cash"},
                  "subAccount": "p1::s1",
                  "currency": "XYZ"
                }
                """;

        assertThat(ParqetJson.read(json, Holding.class).currency()).isEqualTo(Currency.UNKNOWN);
    }

    @Test
    void refusesToSendAnUnknownCurrency() {
        assertThatThrownBy(Currency.UNKNOWN::code)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be sent to the API");
    }
}
