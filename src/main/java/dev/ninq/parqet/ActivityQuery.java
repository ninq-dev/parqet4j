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

import dev.ninq.parqet.internal.Validate;
import dev.ninq.parqet.model.ActivityType;
import dev.ninq.parqet.model.AssetType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which activities to fetch, and how many at a time.
 * <p>
 * Every filter is optional; {@link #all()} fetches everything at the API's default page size. Filters combine with AND across dimensions
 * and OR within one, so asking for {@code BUY} and {@code SELL} of asset type {@code SECURITY} returns security buys and security sells.
 *
 * <pre>{@code
 * var query = ActivityQuery.builder()
 *         .limit(500)
 *         .types(ActivityType.BUY, ActivityType.SELL)
 *         .assetTypes(AssetType.SECURITY)
 *         .build();
 * }</pre>
 *
 * @param limit how many activities one page may hold, 1 to 500, or {@code null} for the API default of 100
 * @param cursor where to resume from, or {@code null} to start at the beginning
 * @param types the activity types to include; empty means all
 * @param assetTypes the asset types to include; empty means all
 * @param holdingIds the holdings to restrict to; empty means all
 */
public record ActivityQuery(Integer limit, String cursor, Set<ActivityType> types, Set<AssetType> assetTypes, List<String> holdingIds) {

    /** The largest page the API will serve. */
    public static final int MAX_LIMIT = 500;

    /**
     * Canonical constructor.
     *
     * @throws IllegalArgumentException if {@code limit} is outside 1 to 500
     */
    public ActivityQuery {
        if (limit != null) {
            Validate.requireInRange(limit, 1, MAX_LIMIT, "limit");
        }
        // Insertion order is preserved so the rendered query string is stable — Set.copyOf is not.
        types = ordered(types);
        assetTypes = ordered(assetTypes);
        holdingIds = Validate.copyOf(holdingIds);
    }

    private static <T> Set<T> ordered(Set<T> values) {
        return values == null || values.isEmpty() ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    /**
     * Returns a query that fetches every activity, unfiltered.
     *
     * @return an unfiltered query
     */
    public static ActivityQuery all() {
        return new ActivityQuery(null, null, Set.of(), Set.of(), List.of());
    }

    /**
     * Starts building a filtered query.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy of this query resuming from the given cursor.
     *
     * @param cursor the cursor from a previous page
     * @return the adjusted query
     */
    public ActivityQuery withCursor(String cursor) {
        return new ActivityQuery(limit, cursor, types, assetTypes, holdingIds);
    }

    /**
     * Renders this query as URL parameters. Multi-valued filters become repeated parameters, which is how the API reads arrays.
     *
     * @return the parameters, in a stable order
     */
    List<Map.Entry<String, String>> toParameters() {
        var params = new ArrayList<Map.Entry<String, String>>();
        if (limit != null) {
            params.add(Map.entry("limit", limit.toString()));
        }
        if (cursor != null) {
            params.add(Map.entry("cursor", cursor));
        }
        types.forEach(t -> params.add(Map.entry("activityType", t.id())));
        assetTypes.forEach(t -> params.add(Map.entry("assetType", t.id())));
        holdingIds.forEach(id -> params.add(Map.entry("holdingId", id)));
        return List.copyOf(params);
    }

    /** Builder for {@link ActivityQuery}. */
    public static final class Builder {

        private Integer limit;
        private String cursor;
        private final Set<ActivityType> types = new LinkedHashSet<>();
        private final Set<AssetType> assetTypes = new LinkedHashSet<>();
        private final List<String> holdingIds = new ArrayList<>();

        private Builder() {
        }

        /**
         * Sets the page size.
         *
         * @param limit how many activities one page may hold, 1 to {@value #MAX_LIMIT}
         * @return this builder
         * @throws IllegalArgumentException if {@code limit} is out of range
         */
        public Builder limit(int limit) {
            this.limit = Validate.requireInRange(limit, 1, MAX_LIMIT, "limit");
            return this;
        }

        /**
         * Resumes from a cursor returned by a previous page.
         *
         * @param cursor the cursor
         * @return this builder
         */
        public Builder cursor(String cursor) {
            this.cursor = cursor;
            return this;
        }

        /**
         * Restricts the result to the given activity types.
         *
         * @param types the activity types to include
         * @return this builder
         */
        public Builder types(ActivityType... types) {
            this.types.addAll(List.of(types));
            return this;
        }

        /**
         * Restricts the result to activities on holdings of the given asset types.
         *
         * @param assetTypes the asset types to include
         * @return this builder
         */
        public Builder assetTypes(AssetType... assetTypes) {
            this.assetTypes.addAll(List.of(assetTypes));
            return this;
        }

        /**
         * Restricts the result to the given holdings.
         *
         * @param holdingIds the holdings to include
         * @return this builder
         */
        public Builder holdings(String... holdingIds) {
            this.holdingIds.addAll(List.of(holdingIds));
            return this;
        }

        /**
         * Builds the query.
         *
         * @return the query
         */
        public ActivityQuery build() {
            return new ActivityQuery(limit, cursor, types, assetTypes, holdingIds);
        }
    }
}
