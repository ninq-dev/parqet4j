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

/** How a custom holding is categorised in the Parqet UI. */
public enum AssetProduct {

    /** A private-equity investment. */
    PRIVATE_EQUITY("private_equity"),

    /** A tangible asset such as art, a vehicle, or a collection. */
    MATERIAL_ASSET("material_asset"),

    /** Anything that fits neither of the above. */
    OTHER("other");

    private static final Map<String, AssetProduct> BY_ID = Stream.of(values()).collect(Collectors.toMap(p -> p.id, Function.identity()));

    private final String id;

    AssetProduct(String id) {
        this.id = id;
    }

    /**
     * Returns the wire representation of this asset product.
     *
     * @return the identifier as the API spells it
     */
    @JsonValue
    public String id() {
        return id;
    }

    /**
     * Maps a wire asset product to a constant.
     *
     * @param id the identifier as it appears in JSON
     * @return the matching constant
     * @throws IllegalArgumentException if the identifier is not known
     */
    @JsonCreator
    public static AssetProduct fromId(String id) {
        var product = BY_ID.get(id);
        if (product == null) {
            throw new IllegalArgumentException("Unknown asset product: " + id);
        }
        return product;
    }
}
