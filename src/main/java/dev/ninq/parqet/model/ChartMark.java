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

/** How to read the timestamp of a {@link PortfolioPerformance.ChartPoint}. */
public enum ChartMark {

    /** The values are taken at the beginning of the day of the point's date. */
    BEGIN_OF_DAY("bod"),

    /** The values are taken at the end of the day of the point's date. */
    END_OF_DAY("eod"),

    /** The values are the most recent ones available on the day of the point's date. */
    MOST_RECENT("most_recent"),

    /** The timestamp is literal — the values are taken at exactly that instant. */
    EXACT_DATE("exact_date");

    private static final Map<String, ChartMark> BY_ID = Stream.of(values()).collect(Collectors.toMap(m -> m.id, Function.identity()));

    private final String id;

    ChartMark(String id) {
        this.id = id;
    }

    /**
     * Returns the wire representation of this mark.
     *
     * @return the identifier as the API spells it
     */
    @JsonValue
    public String id() {
        return id;
    }

    /**
     * Maps a wire mark to a constant.
     *
     * @param id the identifier as it appears in JSON
     * @return the matching constant
     * @throws IllegalArgumentException if the identifier is not known
     */
    @JsonCreator
    public static ChartMark fromId(String id) {
        var mark = BY_ID.get(id);
        if (mark == null) {
            throw new IllegalArgumentException("Unknown chart mark: " + id);
        }
        return mark;
    }
}
