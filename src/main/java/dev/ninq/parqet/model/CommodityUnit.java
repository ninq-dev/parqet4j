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

/** The unit a commodity holding is measured in. */
public enum CommodityUnit {

    /** Troy ounce, spelled {@code oz.tr.} on the wire. */
    TROY_OUNCE("oz.tr."),

    /** Gram. */
    GRAM("g");

    private static final Map<String, CommodityUnit> BY_ID = Stream.of(values()).collect(Collectors.toMap(u -> u.id, Function.identity()));

    private final String id;

    CommodityUnit(String id) {
        this.id = id;
    }

    /**
     * Returns the wire representation of this unit.
     *
     * @return the identifier as the API spells it
     */
    @JsonValue
    public String id() {
        return id;
    }

    /**
     * Maps a wire unit to a constant.
     *
     * @param id the identifier as it appears in JSON
     * @return the matching constant
     * @throws IllegalArgumentException if the identifier is not known
     */
    @JsonCreator
    public static CommodityUnit fromId(String id) {
        var unit = BY_ID.get(id);
        if (unit == null) {
            throw new IllegalArgumentException("Unknown commodity unit: " + id);
        }
        return unit;
    }
}
