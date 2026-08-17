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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.ninq.parqet.internal.Validate;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * What a {@link Holding} is invested in, discriminated by the {@code type} property.
 * <p>
 * Only {@link Security}, {@link Crypto} and {@link Commodity} carry identifying detail; the remaining kinds are described entirely by the
 * holding itself, so they are empty markers. Because the hierarchy is sealed, a {@code switch} over the variants is exhaustive without a
 * default branch, and {@link #type()} gives the discriminator without matching at all.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HoldingAsset.Security.class, name = "security"),
        @JsonSubTypes.Type(value = HoldingAsset.Crypto.class, name = "crypto"),
        @JsonSubTypes.Type(value = HoldingAsset.Commodity.class, name = "commodity"),
        @JsonSubTypes.Type(value = HoldingAsset.Cash.class, name = "cash"),
        @JsonSubTypes.Type(value = HoldingAsset.Custom.class, name = "custom"),
        @JsonSubTypes.Type(value = HoldingAsset.Insurance.class, name = "insurance"),
        @JsonSubTypes.Type(value = HoldingAsset.P2p.class, name = "p2p"),
        @JsonSubTypes.Type(value = HoldingAsset.RealEstate.class, name = "real_estate"),
})
public sealed interface HoldingAsset {

    /**
     * Returns the asset type discriminator carried by this variant.
     *
     * @return the asset type
     */
    AssetType type();

    /**
     * A security identified by ISIN — a stock, ETF, or bond.
     *
     * @param isin the security's ISIN
     * @param name the security's display name
     */
    record Security(String isin, String name) implements HoldingAsset {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code isin} is not a syntactically valid ISIN
         */
        public Security {
            Validate.requireIsin(isin);
            Validate.requireText(name, "name");
        }

        @Override
        public AssetType type() {
            return AssetType.SECURITY;
        }
    }

    /**
     * A cryptocurrency identified by ticker symbol.
     *
     * @param symbol the ticker symbol, for example {@code BTC}
     * @param name the coin's display name
     */
    record Crypto(String symbol, String name) implements HoldingAsset {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if any argument is {@code null}
         */
        public Crypto {
            Validate.requireText(symbol, "symbol");
            Validate.requireText(name, "name");
        }

        @Override
        public AssetType type() {
            return AssetType.CRYPTO;
        }
    }

    /**
     * A physical precious-metal position.
     *
     * @param identifier which metal is held
     * @param name the display name
     * @param unit the unit {@code amount} is expressed in
     * @param amount how much of {@code unit} one share of the holding represents
     * @param purity the fineness in parts per thousand, 1 to 1000
     */
    record Commodity(CommodityIdentifier identifier, String name, CommodityUnit unit, BigDecimal amount, BigDecimal purity)
            implements
                HoldingAsset {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if any argument is {@code null}
         */
        public Commodity {
            Objects.requireNonNull(identifier, "identifier must not be null");
            Validate.requireText(name, "name");
            Objects.requireNonNull(unit, "unit must not be null");
            Validate.requireAmount(amount, "amount");
            Validate.requireAmount(purity, "purity");
        }

        @Override
        public AssetType type() {
            return AssetType.COMMODITY;
        }
    }

    /** A cash account. The holding's own currency and name describe it fully. */
    record Cash() implements HoldingAsset {

        @Override
        public AssetType type() {
            return AssetType.CASH;
        }
    }

    /** A user-defined asset priced by user-managed quotes. */
    record Custom() implements HoldingAsset {

        @Override
        public AssetType type() {
            return AssetType.CUSTOM;
        }
    }

    /** An insurance policy. */
    record Insurance() implements HoldingAsset {

        @Override
        public AssetType type() {
            return AssetType.INSURANCE;
        }
    }

    /** A peer-to-peer lending position. */
    record P2p() implements HoldingAsset {

        @Override
        public AssetType type() {
            return AssetType.P2P;
        }
    }

    /** A real-estate position. */
    record RealEstate() implements HoldingAsset {

        @Override
        public AssetType type() {
            return AssetType.REAL_ESTATE;
        }
    }
}
