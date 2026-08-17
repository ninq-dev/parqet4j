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

/** What an activity did to a holding. */
public enum ActivityType {

    /** Shares or units were bought. */
    BUY("buy"),

    /** Shares or units were sold. */
    SELL("sell"),

    /** A dividend was received. Also used for insurance and P2P payouts. */
    DIVIDEND("dividend"),

    /** Interest was received. */
    INTEREST("interest"),

    /** Shares or units were transferred into the portfolio without a purchase. */
    TRANSFER_IN("transfer_in"),

    /** Shares or units were transferred out of the portfolio without a sale. */
    TRANSFER_OUT("transfer_out"),

    /** A standalone fee or tax booking, not attached to a trade. */
    FEES_TAXES("fees_taxes"),

    /** Cash was paid into a cash holding. */
    DEPOSIT("deposit"),

    /** Cash was withdrawn from a cash holding. */
    WITHDRAWAL("withdrawal");

    private static final Map<String, ActivityType> BY_ID = Stream.of(values()).collect(Collectors.toMap(t -> t.id, Function.identity()));

    private final String id;

    ActivityType(String id) {
        this.id = id;
    }

    /**
     * Returns the wire representation of this activity type.
     *
     * @return the identifier as the API spells it
     */
    @JsonValue
    public String id() {
        return id;
    }

    /**
     * Maps a wire activity type to a constant.
     *
     * @param id the identifier as it appears in JSON
     * @return the matching constant
     * @throws IllegalArgumentException if the identifier is not known
     */
    @JsonCreator
    public static ActivityType fromId(String id) {
        var type = BY_ID.get(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown activity type: " + id);
        }
        return type;
    }
}
