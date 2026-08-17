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

/** The kind of asset a holding tracks. */
public enum AssetType {

    /** A cash account. */
    CASH("cash"),

    /** A security identified by ISIN — stocks, ETFs, bonds. */
    SECURITY("security"),

    /** A cryptocurrency identified by ticker symbol. */
    CRYPTO("crypto"),

    /** A physical commodity — gold, silver, platinum, palladium. */
    COMMODITY("commodity"),

    /** A user-defined asset with user-managed quotes. */
    CUSTOM("custom"),

    /** An insurance policy. */
    INSURANCE("insurance"),

    /** A peer-to-peer lending position. */
    P2P("p2p"),

    /** A real-estate position. */
    REAL_ESTATE("real_estate");

    private static final Map<String, AssetType> BY_ID = Stream.of(values()).collect(Collectors.toMap(t -> t.id, Function.identity()));

    private final String id;

    AssetType(String id) {
        this.id = id;
    }

    /**
     * Returns the wire representation of this asset type.
     *
     * @return the identifier as the API spells it
     */
    @JsonValue
    public String id() {
        return id;
    }

    /**
     * Maps a wire asset type to a constant.
     *
     * @param id the identifier as it appears in JSON
     * @return the matching constant
     * @throws IllegalArgumentException if the identifier is not known
     */
    @JsonCreator
    public static AssetType fromId(String id) {
        var type = BY_ID.get(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown asset type: " + id);
        }
        return type;
    }
}
