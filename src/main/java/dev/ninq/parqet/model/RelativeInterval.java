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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The named look-back windows accepted by the performance endpoint. */
public enum RelativeInterval {

    /** The last day. */
    ONE_DAY("1d"),

    /** The last week. */
    ONE_WEEK("1w"),

    /** Month to date. */
    MONTH_TO_DATE("mtd"),

    /** The last month. */
    ONE_MONTH("1m"),

    /** The last three months. */
    THREE_MONTHS("3m"),

    /** The last six months. */
    SIX_MONTHS("6m"),

    /** The last year. */
    ONE_YEAR("1y"),

    /** Year to date. */
    YEAR_TO_DATE("ytd"),

    /** The last three years. */
    THREE_YEARS("3y"),

    /** The last five years. */
    FIVE_YEARS("5y"),

    /** The last ten years. */
    TEN_YEARS("10y"),

    /** The entire history of the portfolio. This is the API default. */
    MAX("max");

    private static final Map<String, RelativeInterval> BY_ID = Stream.of(values()).collect(Collectors.toMap(i -> i.id, Function.identity()));

    private final String id;

    RelativeInterval(String id) {
        this.id = id;
    }

    /**
     * Returns the wire representation of this interval.
     *
     * @return the identifier as the API spells it
     */
    @JsonValue
    public String id() {
        return id;
    }

    /**
     * Maps a wire interval to a constant.
     *
     * @param id the identifier as it appears in JSON
     * @return the matching constant
     * @throws IllegalArgumentException if the identifier is not known
     */
    @JsonCreator
    public static RelativeInterval fromId(String id) {
        var interval = BY_ID.get(id);
        if (interval == null) {
            throw new IllegalArgumentException("Unknown relative interval: " + id);
        }
        return interval;
    }
}
