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

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.ninq.parqet.internal.Validate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * A holding to create in a portfolio.
 * <p>
 * Unlike activities, each kind of holding has its own endpoint, so the JSON carries no discriminator — this hierarchy exists to make one
 * {@code createHolding} call cover all six, and to keep the fields of each kind apart. Securities and crypto holdings are not created
 * directly: booking an activity against an ISIN or ticker creates them.
 *
 * <pre>{@code
 * var id = portfolio.createHolding(NewHolding.cash("Verrechnungskonto")
 *         .currency(Currency.EUR)
 *         .referenceAccountFor(AssetType.SECURITY, AssetType.CRYPTO)
 *         .externalId("broker-cash-1")
 *         .build());
 * }</pre>
 */
public sealed interface NewHolding {

    /**
     * Returns the asset type of the holding this will create. The client uses it to pick the endpoint.
     *
     * @return the asset type
     */
    @JsonIgnore
    AssetType assetType();

    /**
     * Returns the display name of the holding.
     *
     * @return the name
     */
    String name();

    /**
     * Returns the caller's own identifier for the holding.
     *
     * @return the external id, or {@code null} when unset
     */
    String externalId();

    /**
     * Starts a cash holding.
     *
     * @param name the display name, 1 to 80 characters
     * @return a builder for the holding
     */
    static CashBuilder cash(String name) {
        return new CashBuilder(name);
    }

    /**
     * Starts a commodity holding.
     *
     * @param name the display name, 1 to 80 characters
     * @param identifier which metal is held
     * @return a builder for the holding
     */
    static CommodityBuilder commodity(String name, CommodityIdentifier identifier) {
        return new CommodityBuilder(name, identifier);
    }

    /**
     * Starts a custom holding, priced by quotes the integration supplies.
     *
     * @param name the display name, 1 to 80 characters
     * @param assetProduct how the holding is categorised in the Parqet UI
     * @return a builder for the holding
     */
    static CustomBuilder custom(String name, AssetProduct assetProduct) {
        return new CustomBuilder(name, assetProduct);
    }

    /**
     * Starts a real-estate holding.
     *
     * @param name the display name, 1 to 80 characters
     * @return a builder for the holding
     */
    static RealEstateBuilder realEstate(String name) {
        return new RealEstateBuilder(name);
    }

    /**
     * Starts an insurance holding.
     *
     * @param name the display name, 1 to 80 characters
     * @return a builder for the holding
     */
    static InsuranceBuilder insurance(String name) {
        return new InsuranceBuilder(name);
    }

    /**
     * Starts a P2P holding.
     *
     * @param name the display name, 1 to 80 characters
     * @return a builder for the holding
     */
    static P2pBuilder p2p(String name) {
        return new P2pBuilder(name);
    }

    /**
     * A cash account.
     *
     * @param name the display name
     * @param currency the account currency; the API defaults to {@code EUR} when {@code null}
     * @param referenceAccountFor the asset classes this account settles, never {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record Cash(String name, Currency currency, List<AssetType> referenceAccountFor, String externalId) implements NewHolding {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the name or external id is invalid
         */
        public Cash {
            Validate.requireName(name);
            Validate.checkExternalId(externalId);
            referenceAccountFor = Validate.copyOf(referenceAccountFor);
        }

        @Override
        public AssetType assetType() {
            return AssetType.CASH;
        }
    }

    /**
     * A physical precious-metal position.
     *
     * @param name the display name
     * @param identifier which metal is held
     * @param definition how much metal, at what purity, in which unit
     * @param externalId the caller's own identifier, or {@code null}
     */
    record Commodity(String name, CommodityIdentifier identifier, Definition definition, String externalId) implements NewHolding {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code identifier} or {@code definition} is {@code null}
         * @throws IllegalArgumentException if the name or external id is invalid
         */
        public Commodity {
            Validate.requireName(name);
            Objects.requireNonNull(identifier, "identifier must not be null");
            Objects.requireNonNull(definition, "definition must not be null");
            Validate.checkExternalId(externalId);
        }

        @Override
        public AssetType assetType() {
            return AssetType.COMMODITY;
        }

        /**
         * How much metal one unit of the holding represents.
         *
         * @param amount the quantity, at least 0.00001
         * @param purity the fineness in parts per thousand, 1 to 1000
         * @param unit the unit {@code amount} is expressed in
         */
        public record Definition(BigDecimal amount, BigDecimal purity, CommodityUnit unit) {

            /**
             * Canonical constructor.
             *
             * @throws NullPointerException if any argument is {@code null}
             * @throws IllegalArgumentException if {@code amount} or {@code purity} is out of range
             */
            public Definition {
                Validate.requirePositive(amount, "amount");
                Validate.requirePositive(purity, "purity");
                if (purity.compareTo(BigDecimal.valueOf(1000)) > 0) {
                    throw new IllegalArgumentException("purity must be at most 1000, was " + purity.toPlainString());
                }
                Objects.requireNonNull(unit, "unit must not be null");
            }
        }
    }

    /**
     * A user-defined asset, valued by the quotes the integration pushes for it.
     *
     * @param name the display name
     * @param assetProduct how the holding is categorised in the Parqet UI
     * @param imageData a base64-encoded 256x256 PNG of at most 512 KiB, or {@code null}
     * @param quotes initial prices for the asset, never {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record Custom(String name, AssetProduct assetProduct, String imageData, List<Quote> quotes, String externalId) implements NewHolding {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code assetProduct} is {@code null}
         * @throws IllegalArgumentException if the name or external id is invalid
         */
        public Custom {
            Validate.requireName(name);
            Objects.requireNonNull(assetProduct, "assetProduct must not be null");
            Validate.checkExternalId(externalId);
            quotes = Validate.copyOf(quotes);
        }

        @Override
        public AssetType assetType() {
            return AssetType.CUSTOM;
        }
    }

    /**
     * A real-estate position.
     *
     * @param name the display name
     * @param imageData a base64-encoded 256x256 PNG of at most 512 KiB, or {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record RealEstate(String name, String imageData, String externalId) implements NewHolding {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the name or external id is invalid
         */
        public RealEstate {
            Validate.requireName(name);
            Validate.checkExternalId(externalId);
        }

        @Override
        public AssetType assetType() {
            return AssetType.REAL_ESTATE;
        }
    }

    /**
     * An insurance policy.
     *
     * @param name the display name
     * @param imageData a base64-encoded 256x256 PNG of at most 512 KiB, or {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record Insurance(String name, String imageData, String externalId) implements NewHolding {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the name or external id is invalid
         */
        public Insurance {
            Validate.requireName(name);
            Validate.checkExternalId(externalId);
        }

        @Override
        public AssetType assetType() {
            return AssetType.INSURANCE;
        }
    }

    /**
     * A peer-to-peer lending position.
     *
     * @param name the display name
     * @param imageData a base64-encoded 256x256 PNG of at most 512 KiB, or {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record P2p(String name, String imageData, String externalId) implements NewHolding {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the name or external id is invalid
         */
        public P2p {
            Validate.requireName(name);
            Validate.checkExternalId(externalId);
        }

        @Override
        public AssetType assetType() {
            return AssetType.P2P;
        }
    }

    /**
     * Shared builder state: the fields every kind of holding carries.
     *
     * @param <B> the concrete builder type, so the setters here return it rather than this base
     */
    abstract class BaseBuilder<B extends BaseBuilder<B>> {

        final String name;
        String externalId;

        BaseBuilder(String name) {
            this.name = Validate.requireName(name);
        }

        @SuppressWarnings("unchecked")
        final B self() {
            return (B) this;
        }

        /**
         * Sets the caller's own identifier for the holding, which later syncs can address it by.
         *
         * @param externalId the external id; must match {@code ^[A-Za-z0-9\-_]+$} and be at most 255 characters
         * @return this builder
         */
        public final B externalId(String externalId) {
            this.externalId = externalId;
            return self();
        }
    }

    /**
     * Shared builder state for the kinds that accept a logo.
     *
     * @param <B> the concrete builder type
     */
    abstract class ImageBuilder<B extends ImageBuilder<B>> extends BaseBuilder<B> {

        String imageData;

        ImageBuilder(String name) {
            super(name);
        }

        /**
         * Sets a logo for the holding.
         *
         * @param imageData a base64-encoded 256x256 PNG of at most 512 KiB
         * @return this builder
         */
        public final B imageData(String imageData) {
            this.imageData = imageData;
            return self();
        }
    }

    /** Builds a {@link Cash} holding. */
    final class CashBuilder extends BaseBuilder<CashBuilder> {

        private Currency currency;
        private List<AssetType> referenceAccountFor = List.of();

        CashBuilder(String name) {
            super(name);
        }

        /**
         * Sets the account currency. Left unset, the API defaults to {@code EUR}.
         *
         * @param currency the currency
         * @return this builder
         */
        public CashBuilder currency(Currency currency) {
            this.currency = currency;
            return this;
        }

        /**
         * Declares which asset classes settle through this cash account.
         *
         * @param assetTypes the asset classes
         * @return this builder
         */
        public CashBuilder referenceAccountFor(AssetType... assetTypes) {
            this.referenceAccountFor = List.of(assetTypes);
            return this;
        }

        /**
         * Builds the holding.
         *
         * @return the holding to create
         */
        public Cash build() {
            return new Cash(name, currency, referenceAccountFor, externalId);
        }
    }

    /** Builds a {@link Commodity} holding. */
    final class CommodityBuilder extends BaseBuilder<CommodityBuilder> {

        private final CommodityIdentifier identifier;
        private Commodity.Definition definition;

        CommodityBuilder(String name, CommodityIdentifier identifier) {
            super(name);
            this.identifier = Objects.requireNonNull(identifier, "identifier must not be null");
        }

        /**
         * Sets how much metal the holding represents. Required.
         *
         * @param amount the quantity
         * @param purity the fineness in parts per thousand, 1 to 1000
         * @param unit the unit {@code amount} is expressed in
         * @return this builder
         */
        public CommodityBuilder definition(BigDecimal amount, BigDecimal purity, CommodityUnit unit) {
            this.definition = new Commodity.Definition(amount, purity, unit);
            return this;
        }

        /**
         * Builds the holding.
         *
         * @return the holding to create
         * @throws NullPointerException if the definition is unset
         */
        public Commodity build() {
            return new Commodity(name, identifier, definition, externalId);
        }
    }

    /** Builds a {@link Custom} holding. */
    final class CustomBuilder extends ImageBuilder<CustomBuilder> {

        private final AssetProduct assetProduct;
        private List<Quote> quotes = List.of();

        CustomBuilder(String name, AssetProduct assetProduct) {
            super(name);
            this.assetProduct = Objects.requireNonNull(assetProduct, "assetProduct must not be null");
        }

        /**
         * Sets the initial prices for the asset. More can be pushed later with a {@link QuoteUpdate}.
         *
         * @param quotes the prices
         * @return this builder
         */
        public CustomBuilder quotes(List<Quote> quotes) {
            this.quotes = Validate.copyOf(quotes);
            return this;
        }

        /**
         * Builds the holding.
         *
         * @return the holding to create
         */
        public Custom build() {
            return new Custom(name, assetProduct, imageData, quotes, externalId);
        }
    }

    /** Builds a {@link RealEstate} holding. */
    final class RealEstateBuilder extends ImageBuilder<RealEstateBuilder> {

        RealEstateBuilder(String name) {
            super(name);
        }

        /**
         * Builds the holding.
         *
         * @return the holding to create
         */
        public RealEstate build() {
            return new RealEstate(name, imageData, externalId);
        }
    }

    /** Builds an {@link Insurance} holding. */
    final class InsuranceBuilder extends ImageBuilder<InsuranceBuilder> {

        InsuranceBuilder(String name) {
            super(name);
        }

        /**
         * Builds the holding.
         *
         * @return the holding to create
         */
        public Insurance build() {
            return new Insurance(name, imageData, externalId);
        }
    }

    /** Builds a {@link P2p} holding. */
    final class P2pBuilder extends ImageBuilder<P2pBuilder> {

        P2pBuilder(String name) {
            super(name);
        }

        /**
         * Builds the holding.
         *
         * @return the holding to create
         */
        public P2p build() {
            return new P2p(name, imageData, externalId);
        }
    }
}
