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

import dev.ninq.parqet.model.ActivityType;
import dev.ninq.parqet.model.AssetType;
import org.junit.jupiter.api.Test;

class ActivityQueryTest {

    @Test
    void sendsNothingWhenNothingIsFiltered() {
        assertThat(ActivityQuery.all().toParameters()).isEmpty();
    }

    @Test
    void keepsFilterOrderStableAcrossCalls() {
        var query = ActivityQuery.builder()
                .types(ActivityType.SELL, ActivityType.BUY, ActivityType.DIVIDEND)
                .build();

        assertThat(query.toParameters()).isEqualTo(query.toParameters());
        assertThat(query.toParameters()).extracting(java.util.Map.Entry::getValue).containsExactly("sell", "buy", "dividend");
    }

    @Test
    void deduplicatesRepeatedFilterValues() {
        var query = ActivityQuery.builder()
                .types(ActivityType.BUY, ActivityType.BUY)
                .assetTypes(AssetType.SECURITY, AssetType.SECURITY)
                .build();

        assertThat(query.types()).containsExactly(ActivityType.BUY);
        assertThat(query.assetTypes()).containsExactly(AssetType.SECURITY);
    }

    @Test
    void carriesTheCursorForwardWithoutTouchingTheFilters() {
        var first = ActivityQuery.builder().limit(200).types(ActivityType.BUY).build();

        var next = first.withCursor("c-2");

        assertThat(next.cursor()).isEqualTo("c-2");
        assertThat(next.limit()).isEqualTo(200);
        assertThat(next.types()).isEqualTo(first.types());
        assertThat(first.cursor()).isNull();
    }

    @Test
    void refusesAPageSizeTheApiWouldReject() {
        assertThatThrownBy(() -> ActivityQuery.builder().limit(0)).hasMessageContaining("between 1 and 500");
        assertThatThrownBy(() -> ActivityQuery.builder().limit(501)).hasMessageContaining("between 1 and 500");
        assertThat(ActivityQuery.builder().limit(ActivityQuery.MAX_LIMIT).build().limit()).isEqualTo(500);
    }

    @Test
    void exposesFiltersAsUnmodifiableCollections() {
        var query = ActivityQuery.builder().types(ActivityType.BUY).holdings("h1").build();

        assertThatThrownBy(() -> query.types().add(ActivityType.SELL)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> query.holdingIds().add("h2")).isInstanceOf(UnsupportedOperationException.class);
    }
}
