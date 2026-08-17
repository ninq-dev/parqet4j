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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.ninq.parqet.internal.Validate;

/**
 * How a returned {@link Activity} identifies the asset it was booked against, discriminated by the {@code assetIdentifierType} property.
 * <p>
 * Securities and crypto name the asset globally; everything else points back at the holding it belongs to. This mirrors the shape the API
 * returns on reads — for writes, see {@link NewActivity}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "assetIdentifierType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActivityAsset.Security.class, name = "isin"),
        @JsonSubTypes.Type(value = ActivityAsset.Crypto.class, name = "crypto_symbol"),
        @JsonSubTypes.Type(value = ActivityAsset.Commodity.class, name = "commodity"),
        @JsonSubTypes.Type(value = ActivityAsset.Cash.class, name = "cash"),
        @JsonSubTypes.Type(value = ActivityAsset.Custom.class, name = "custom_asset"),
        @JsonSubTypes.Type(value = ActivityAsset.Insurance.class, name = "insurance"),
        @JsonSubTypes.Type(value = ActivityAsset.P2p.class, name = "p2p"),
        @JsonSubTypes.Type(value = ActivityAsset.RealEstate.class, name = "real_estate"),
})
public sealed interface ActivityAsset {

    /**
     * A security identified by ISIN.
     *
     * @param isin the security's ISIN
     */
    record Security(String isin) implements ActivityAsset {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code isin} is {@code null}
         * @throws IllegalArgumentException if {@code isin} is not a syntactically valid ISIN
         */
        public Security {
            Validate.requireIsin(isin);
        }
    }

    /**
     * A cryptocurrency identified by ticker symbol.
     *
     * @param symbol the ticker symbol, for example {@code BTC}
     */
    record Crypto(String symbol) implements ActivityAsset {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code symbol} is {@code null}
         */
        public Crypto {
            Validate.requireText(symbol, "symbol");
        }
    }

    /**
     * A commodity, identified by its display name on reads.
     *
     * @param name the commodity's display name, for example {@code Gold}
     */
    record Commodity(String name) implements ActivityAsset {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code name} is {@code null}
         */
        public Commodity {
            Validate.requireText(name, "name");
        }
    }

    /**
     * A cash account, identified by the holding it belongs to.
     *
     * @param holdingId the id of the cash holding
     */
    record Cash(@JsonProperty("holding_id") String holdingId) implements ActivityAsset {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code holdingId} is {@code null}
         */
        public Cash {
            Validate.requireText(holdingId, "holdingId");
        }
    }

    /**
     * A user-defined asset, identified by the holding it belongs to.
     *
     * @param holdingId the id of the custom holding
     */
    record Custom(@JsonProperty("holding_id") String holdingId) implements ActivityAsset {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code holdingId} is {@code null}
         */
        public Custom {
            Validate.requireText(holdingId, "holdingId");
        }
    }

    /**
     * An insurance policy, identified by the holding it belongs to.
     *
     * @param holdingId the id of the insurance holding
     */
    record Insurance(@JsonProperty("holding_id") String holdingId) implements ActivityAsset {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code holdingId} is {@code null}
         */
        public Insurance {
            Validate.requireText(holdingId, "holdingId");
        }
    }

    /**
     * A peer-to-peer lending position, identified by the holding it belongs to.
     *
     * @param holdingId the id of the P2P holding
     */
    record P2p(@JsonProperty("holding_id") String holdingId) implements ActivityAsset {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code holdingId} is {@code null}
         */
        public P2p {
            Validate.requireText(holdingId, "holdingId");
        }
    }

    /**
     * A real-estate position, identified by the holding it belongs to.
     *
     * @param holdingId the id of the real-estate holding
     */
    record RealEstate(@JsonProperty("holding_id") String holdingId) implements ActivityAsset {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code holdingId} is {@code null}
         */
        public RealEstate {
            Validate.requireText(holdingId, "holdingId");
        }
    }
}
