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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * An activity to book, as sent to {@code POST /portfolios/{portfolioId}/activities}.
 * <p>
 * The eight variants differ in how they name the asset and how they measure it. Securities and crypto name the asset globally, by ISIN or
 * ticker; the other six point at an existing holding. Securities, crypto, commodities, real estate and custom assets are measured in
 * {@code shares} at a {@code price}; cash, insurance and P2P positions are measured by a single {@code amount}.
 * <p>
 * Build one through the static factories, each of which returns a builder that only offers the fields its variant actually has:
 *
 * <pre>{@code
 * var buy = NewActivity.security(ActivityType.BUY, "US0378331005")
 *         .shares(new BigDecimal("10"))
 *         .price(new BigDecimal("234.20"))
 *         .currency(Currency.USD)
 *         .datetime(Instant.parse("2025-11-17T09:33:39.892Z"))
 *         .fee(new BigDecimal("1.00"))
 *         .broker(Broker.TRADE_REPUBLIC)
 *         .build();
 * }</pre>
 * <p>
 * Set {@code externalId} to your own identifier for the booking — it is what lets a later sync recognise an activity it already sent.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "assetIdentifierType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = NewActivity.Security.class, name = "isin"),
        @JsonSubTypes.Type(value = NewActivity.Crypto.class, name = "crypto_symbol"),
        @JsonSubTypes.Type(value = NewActivity.Commodity.class, name = "commodity"),
        @JsonSubTypes.Type(value = NewActivity.Cash.class, name = "cash"),
        @JsonSubTypes.Type(value = NewActivity.CustomAsset.class, name = "custom_asset"),
        @JsonSubTypes.Type(value = NewActivity.Insurance.class, name = "insurance"),
        @JsonSubTypes.Type(value = NewActivity.P2p.class, name = "p2p"),
        @JsonSubTypes.Type(value = NewActivity.RealEstate.class, name = "real_estate"),
})
public sealed interface NewActivity {

    /**
     * Returns what this activity does to the holding.
     *
     * @return the activity type
     */
    ActivityType type();

    /**
     * Returns the currency every monetary field on this activity is expressed in.
     *
     * @return the currency
     */
    Currency currency();

    /**
     * Returns when the activity happened.
     *
     * @return the booking timestamp
     */
    Instant datetime();

    /**
     * Returns the caller's own identifier for this booking.
     *
     * @return the external id, or {@code null} when unset
     */
    String externalId();

    /**
     * Starts a security activity, identified by ISIN.
     *
     * @param type what the activity does
     * @param isin the security's ISIN
     * @return a builder for the activity
     * @throws IllegalArgumentException if {@code isin} is not a syntactically valid ISIN
     */
    static SecurityBuilder security(ActivityType type, String isin) {
        return new SecurityBuilder(type, isin);
    }

    /**
     * Starts a crypto activity, identified by ticker symbol.
     *
     * @param type what the activity does
     * @param symbol the ticker symbol, for example {@code BTC}
     * @return a builder for the activity
     */
    static CryptoBuilder crypto(ActivityType type, String symbol) {
        return new CryptoBuilder(type, symbol);
    }

    /**
     * Starts a commodity activity on an existing commodity holding.
     *
     * @param type what the activity does
     * @param holdingId the commodity holding to book against
     * @return a builder for the activity
     */
    static CommodityBuilder commodity(ActivityType type, String holdingId) {
        return new CommodityBuilder(type, holdingId);
    }

    /**
     * Starts a real-estate activity on an existing real-estate holding.
     *
     * @param type what the activity does
     * @param holdingId the real-estate holding to book against
     * @return a builder for the activity
     */
    static RealEstateBuilder realEstate(ActivityType type, String holdingId) {
        return new RealEstateBuilder(type, holdingId);
    }

    /**
     * Starts an activity on an existing custom holding.
     *
     * @param type what the activity does
     * @param holdingId the custom holding to book against
     * @return a builder for the activity
     */
    static CustomAssetBuilder customAsset(ActivityType type, String holdingId) {
        return new CustomAssetBuilder(type, holdingId);
    }

    /**
     * Starts a cash activity on an existing cash holding.
     *
     * @param type what the activity does; {@link ActivityType#DEPOSIT} and {@link ActivityType#WITHDRAWAL} are the usual choices
     * @param holdingId the cash holding to book against
     * @return a builder for the activity
     */
    static CashBuilder cash(ActivityType type, String holdingId) {
        return new CashBuilder(type, holdingId);
    }

    /**
     * Starts an insurance activity on an existing insurance holding. Payouts are booked as {@link ActivityType#DIVIDEND}; the API rejects
     * {@link ActivityType#INTEREST} here.
     *
     * @param type what the activity does
     * @param holdingId the insurance holding to book against
     * @return a builder for the activity
     */
    static InsuranceBuilder insurance(ActivityType type, String holdingId) {
        return new InsuranceBuilder(type, holdingId);
    }

    /**
     * Starts a P2P activity on an existing P2P holding. Payouts are booked as {@link ActivityType#DIVIDEND}; the API rejects
     * {@link ActivityType#INTEREST} here.
     *
     * @param type what the activity does
     * @param holdingId the P2P holding to book against
     * @return a builder for the activity
     */
    static P2pBuilder p2p(ActivityType type, String holdingId) {
        return new P2pBuilder(type, holdingId);
    }

    /**
     * A trade in a security, identified by ISIN.
     *
     * @param type what the activity does
     * @param isin the security's ISIN
     * @param shares the number of shares
     * @param price the price per share
     * @param currency the currency of {@code price}, {@code tax} and {@code fee}
     * @param datetime when the activity happened
     * @param tax the tax paid, or {@code null}
     * @param fee the fee paid, or {@code null}
     * @param description a user-visible note, or {@code null}
     * @param broker where the activity originated, or {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record Security(ActivityType type, String isin, BigDecimal shares, BigDecimal price, Currency currency, Instant datetime, BigDecimal tax,
            BigDecimal fee, String description, Broker broker, String externalId) implements NewActivity {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the ISIN, external id, shares or price are invalid
         */
        public Security {
            Common.check(type, currency, datetime, externalId);
            Validate.requireIsin(isin);
            Validate.requirePositive(shares, "shares");
            Validate.requireAmount(price, "price");
        }
    }

    /**
     * A trade in a cryptocurrency, identified by ticker symbol.
     *
     * @param type what the activity does
     * @param symbol the ticker symbol
     * @param shares the number of units
     * @param price the price per unit
     * @param currency the currency of {@code price}, {@code tax} and {@code fee}
     * @param datetime when the activity happened
     * @param tax the tax paid, or {@code null}
     * @param fee the fee paid, or {@code null}
     * @param description a user-visible note, or {@code null}
     * @param broker where the activity originated, or {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record Crypto(ActivityType type, String symbol, BigDecimal shares, BigDecimal price, Currency currency, Instant datetime, BigDecimal tax,
            BigDecimal fee, String description, Broker broker, String externalId) implements NewActivity {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the symbol, external id, shares or price are invalid
         */
        public Crypto {
            Common.check(type, currency, datetime, externalId);
            Validate.requireText(symbol, "symbol");
            Validate.requirePositive(shares, "shares");
            Validate.requireAmount(price, "price");
        }
    }

    /**
     * A trade in a commodity holding.
     *
     * @param type what the activity does
     * @param holdingId the commodity holding
     * @param shares the number of units
     * @param price the price per unit
     * @param currency the currency of {@code price}, {@code tax} and {@code fee}
     * @param datetime when the activity happened
     * @param tax the tax paid, or {@code null}
     * @param fee the fee paid, or {@code null}
     * @param description a user-visible note, or {@code null}
     * @param broker where the activity originated, or {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record Commodity(ActivityType type, @JsonProperty("holding_id") String holdingId, BigDecimal shares, BigDecimal price, Currency currency,
            Instant datetime, BigDecimal tax, BigDecimal fee, String description, Broker broker, String externalId) implements NewActivity {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the holding id, external id, shares or price are invalid
         */
        public Commodity {
            Common.check(type, currency, datetime, externalId);
            Validate.requireText(holdingId, "holdingId");
            Validate.requirePositive(shares, "shares");
            Validate.requireAmount(price, "price");
        }
    }

    /**
     * A trade in a real-estate holding.
     *
     * @param type what the activity does
     * @param holdingId the real-estate holding
     * @param shares the number of units
     * @param price the price per unit
     * @param currency the currency of {@code price}, {@code tax} and {@code fee}
     * @param datetime when the activity happened
     * @param tax the tax paid, or {@code null}
     * @param fee the fee paid, or {@code null}
     * @param description a user-visible note, or {@code null}
     * @param broker where the activity originated, or {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record RealEstate(ActivityType type, @JsonProperty("holding_id") String holdingId, BigDecimal shares, BigDecimal price,
            Currency currency, Instant datetime, BigDecimal tax, BigDecimal fee, String description, Broker broker, String externalId)
            implements
                NewActivity {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the holding id, external id, shares or price are invalid
         */
        public RealEstate {
            Common.check(type, currency, datetime, externalId);
            Validate.requireText(holdingId, "holdingId");
            Validate.requirePositive(shares, "shares");
            Validate.requireAmount(price, "price");
        }
    }

    /**
     * A trade in a custom holding.
     *
     * @param type what the activity does
     * @param holdingId the custom holding
     * @param shares the number of units
     * @param price the price per unit
     * @param currency the currency of {@code price}, {@code tax} and {@code fee}
     * @param datetime when the activity happened
     * @param tax the tax paid, or {@code null}
     * @param fee the fee paid, or {@code null}
     * @param description a user-visible note, or {@code null}
     * @param broker where the activity originated, or {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record CustomAsset(ActivityType type, @JsonProperty("holding_id") String holdingId, BigDecimal shares, BigDecimal price,
            Currency currency, Instant datetime, BigDecimal tax, BigDecimal fee, String description, Broker broker, String externalId)
            implements
                NewActivity {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the holding id, external id, shares or price are invalid
         */
        public CustomAsset {
            Common.check(type, currency, datetime, externalId);
            Validate.requireText(holdingId, "holdingId");
            Validate.requirePositive(shares, "shares");
            Validate.requireAmount(price, "price");
        }
    }

    /**
     * A movement on a cash holding.
     *
     * @param type what the activity does
     * @param holdingId the cash holding
     * @param amount the amount moved
     * @param currency the currency of {@code amount}, {@code tax} and {@code fee}
     * @param datetime when the activity happened
     * @param tax the tax paid, or {@code null}
     * @param fee the fee paid, or {@code null}
     * @param description a user-visible note, or {@code null}
     * @param broker where the activity originated, or {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record Cash(ActivityType type, @JsonProperty("holding_id") String holdingId, BigDecimal amount, Currency currency, Instant datetime,
            BigDecimal tax, BigDecimal fee, String description, Broker broker, String externalId) implements NewActivity {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the holding id, external id or amount are invalid
         */
        public Cash {
            Common.check(type, currency, datetime, externalId);
            Validate.requireText(holdingId, "holdingId");
            Validate.requireAmount(amount, "amount");
        }
    }

    /**
     * A movement on an insurance holding.
     *
     * @param type what the activity does; {@link ActivityType#INTEREST} is rejected by the API
     * @param holdingId the insurance holding
     * @param amount the amount moved
     * @param currency the currency of {@code amount}, {@code tax} and {@code fee}
     * @param datetime when the activity happened
     * @param tax the tax paid, or {@code null}
     * @param fee the fee paid, or {@code null}
     * @param description a user-visible note, or {@code null}
     * @param broker where the activity originated, or {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record Insurance(ActivityType type, @JsonProperty("holding_id") String holdingId, BigDecimal amount, Currency currency,
            Instant datetime, BigDecimal tax, BigDecimal fee, String description, Broker broker, String externalId) implements NewActivity {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the holding id, external id or amount are invalid, or if {@code type} is
         *             {@link ActivityType#INTEREST}
         */
        public Insurance {
            Common.check(type, currency, datetime, externalId);
            Common.rejectInterest(type, "insurance");
            Validate.requireText(holdingId, "holdingId");
            Validate.requireAmount(amount, "amount");
        }
    }

    /**
     * A movement on a P2P holding.
     *
     * @param type what the activity does; {@link ActivityType#INTEREST} is rejected by the API
     * @param holdingId the P2P holding
     * @param amount the amount moved
     * @param currency the currency of {@code amount}, {@code tax} and {@code fee}
     * @param datetime when the activity happened
     * @param tax the tax paid, or {@code null}
     * @param fee the fee paid, or {@code null}
     * @param description a user-visible note, or {@code null}
     * @param broker where the activity originated, or {@code null}
     * @param externalId the caller's own identifier, or {@code null}
     */
    record P2p(ActivityType type, @JsonProperty("holding_id") String holdingId, BigDecimal amount, Currency currency, Instant datetime,
            BigDecimal tax, BigDecimal fee, String description, Broker broker, String externalId) implements NewActivity {

        /**
         * Canonical constructor.
         *
         * @throws IllegalArgumentException if the holding id, external id or amount are invalid, or if {@code type} is
         *             {@link ActivityType#INTEREST}
         */
        public P2p {
            Common.check(type, currency, datetime, externalId);
            Common.rejectInterest(type, "P2P");
            Validate.requireText(holdingId, "holdingId");
            Validate.requireAmount(amount, "amount");
        }
    }

    /** Checks shared by every variant's canonical constructor. */
    final class Common {

        private Common() {
        }

        static void check(ActivityType type, Currency currency, Instant datetime, String externalId) {
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(currency, "currency must not be null");
            Objects.requireNonNull(datetime, "datetime must not be null");
            Validate.checkExternalId(externalId);
        }

        static void rejectInterest(ActivityType type, String kind) {
            if (type == ActivityType.INTEREST) {
                throw new IllegalArgumentException(kind + " activities cannot be of type interest; use dividend for payouts");
            }
        }
    }

    /**
     * Shared builder state: the fields every variant carries.
     *
     * @param <B> the concrete builder type, so the setters here return it rather than this base
     */
    abstract class BaseBuilder<B extends BaseBuilder<B>> {

        final ActivityType type;
        Currency currency;
        Instant datetime;
        BigDecimal tax;
        BigDecimal fee;
        String description;
        Broker broker;
        String externalId;

        BaseBuilder(ActivityType type) {
            this.type = Objects.requireNonNull(type, "type must not be null");
        }

        @SuppressWarnings("unchecked")
        final B self() {
            return (B) this;
        }

        /**
         * Sets the currency of every monetary field on this activity. Required.
         *
         * @param currency the currency
         * @return this builder
         */
        public final B currency(Currency currency) {
            this.currency = currency;
            return self();
        }

        /**
         * Sets when the activity happened. Required.
         *
         * @param datetime the booking timestamp
         * @return this builder
         */
        public final B datetime(Instant datetime) {
            this.datetime = datetime;
            return self();
        }

        /**
         * Sets the tax paid on this activity.
         *
         * @param tax the tax, in {@link #currency(Currency)}
         * @return this builder
         */
        public final B tax(BigDecimal tax) {
            this.tax = tax;
            return self();
        }

        /**
         * Sets the fee paid on this activity.
         *
         * @param fee the fee, in {@link #currency(Currency)}
         * @return this builder
         */
        public final B fee(BigDecimal fee) {
            this.fee = fee;
            return self();
        }

        /**
         * Sets a note shown to the user in Parqet.
         *
         * @param description the description
         * @return this builder
         */
        public final B description(String description) {
            this.description = description;
            return self();
        }

        /**
         * Sets where the activity originated.
         *
         * @param broker the broker
         * @return this builder
         */
        public final B broker(Broker broker) {
            this.broker = broker;
            return self();
        }

        /**
         * Sets the caller's own identifier for this booking, which a later sync can use to recognise it.
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
     * Shared builder state for the variants measured in shares at a price.
     *
     * @param <B> the concrete builder type
     */
    abstract class QuantityBuilder<B extends QuantityBuilder<B>> extends BaseBuilder<B> {

        BigDecimal shares;
        BigDecimal price;

        QuantityBuilder(ActivityType type) {
            super(type);
        }

        /**
         * Sets the number of shares or units. Required.
         *
         * @param shares the quantity; must be greater than zero
         * @return this builder
         */
        public final B shares(BigDecimal shares) {
            this.shares = shares;
            return self();
        }

        /**
         * Sets the price per share or unit. Required.
         *
         * @param price the unit price, in {@link #currency(Currency)}
         * @return this builder
         */
        public final B price(BigDecimal price) {
            this.price = price;
            return self();
        }
    }

    /**
     * Shared builder state for the variants measured by a single amount.
     *
     * @param <B> the concrete builder type
     */
    abstract class AmountBuilder<B extends AmountBuilder<B>> extends BaseBuilder<B> {

        BigDecimal amount;

        AmountBuilder(ActivityType type) {
            super(type);
        }

        /**
         * Sets the amount moved. Required.
         *
         * @param amount the amount, in {@link #currency(Currency)}
         * @return this builder
         */
        public final B amount(BigDecimal amount) {
            this.amount = amount;
            return self();
        }
    }

    /** Builds a {@link Security} activity. */
    final class SecurityBuilder extends QuantityBuilder<SecurityBuilder> {

        private final String isin;

        SecurityBuilder(ActivityType type, String isin) {
            super(type);
            this.isin = Validate.requireIsin(isin);
        }

        /**
         * Builds the activity.
         *
         * @return the activity to send
         * @throws NullPointerException if a required field is unset
         * @throws IllegalArgumentException if a field is out of range
         */
        public Security build() {
            return new Security(type, isin, shares, price, currency, datetime, tax, fee, description, broker, externalId);
        }
    }

    /** Builds a {@link Crypto} activity. */
    final class CryptoBuilder extends QuantityBuilder<CryptoBuilder> {

        private final String symbol;

        CryptoBuilder(ActivityType type, String symbol) {
            super(type);
            this.symbol = Validate.requireText(symbol, "symbol");
        }

        /**
         * Builds the activity.
         *
         * @return the activity to send
         * @throws NullPointerException if a required field is unset
         * @throws IllegalArgumentException if a field is out of range
         */
        public Crypto build() {
            return new Crypto(type, symbol, shares, price, currency, datetime, tax, fee, description, broker, externalId);
        }
    }

    /** Builds a {@link Commodity} activity. */
    final class CommodityBuilder extends QuantityBuilder<CommodityBuilder> {

        private final String holdingId;

        CommodityBuilder(ActivityType type, String holdingId) {
            super(type);
            this.holdingId = Validate.requireText(holdingId, "holdingId");
        }

        /**
         * Builds the activity.
         *
         * @return the activity to send
         * @throws NullPointerException if a required field is unset
         * @throws IllegalArgumentException if a field is out of range
         */
        public Commodity build() {
            return new Commodity(type, holdingId, shares, price, currency, datetime, tax, fee, description, broker, externalId);
        }
    }

    /** Builds a {@link RealEstate} activity. */
    final class RealEstateBuilder extends QuantityBuilder<RealEstateBuilder> {

        private final String holdingId;

        RealEstateBuilder(ActivityType type, String holdingId) {
            super(type);
            this.holdingId = Validate.requireText(holdingId, "holdingId");
        }

        /**
         * Builds the activity.
         *
         * @return the activity to send
         * @throws NullPointerException if a required field is unset
         * @throws IllegalArgumentException if a field is out of range
         */
        public RealEstate build() {
            return new RealEstate(type, holdingId, shares, price, currency, datetime, tax, fee, description, broker, externalId);
        }
    }

    /** Builds a {@link CustomAsset} activity. */
    final class CustomAssetBuilder extends QuantityBuilder<CustomAssetBuilder> {

        private final String holdingId;

        CustomAssetBuilder(ActivityType type, String holdingId) {
            super(type);
            this.holdingId = Validate.requireText(holdingId, "holdingId");
        }

        /**
         * Builds the activity.
         *
         * @return the activity to send
         * @throws NullPointerException if a required field is unset
         * @throws IllegalArgumentException if a field is out of range
         */
        public CustomAsset build() {
            return new CustomAsset(type, holdingId, shares, price, currency, datetime, tax, fee, description, broker, externalId);
        }
    }

    /** Builds a {@link Cash} activity. */
    final class CashBuilder extends AmountBuilder<CashBuilder> {

        private final String holdingId;

        CashBuilder(ActivityType type, String holdingId) {
            super(type);
            this.holdingId = Validate.requireText(holdingId, "holdingId");
        }

        /**
         * Builds the activity.
         *
         * @return the activity to send
         * @throws NullPointerException if a required field is unset
         * @throws IllegalArgumentException if a field is out of range
         */
        public Cash build() {
            return new Cash(type, holdingId, amount, currency, datetime, tax, fee, description, broker, externalId);
        }
    }

    /** Builds an {@link Insurance} activity. */
    final class InsuranceBuilder extends AmountBuilder<InsuranceBuilder> {

        private final String holdingId;

        InsuranceBuilder(ActivityType type, String holdingId) {
            super(type);
            this.holdingId = Validate.requireText(holdingId, "holdingId");
        }

        /**
         * Builds the activity.
         *
         * @return the activity to send
         * @throws NullPointerException if a required field is unset
         * @throws IllegalArgumentException if a field is out of range
         */
        public Insurance build() {
            return new Insurance(type, holdingId, amount, currency, datetime, tax, fee, description, broker, externalId);
        }
    }

    /** Builds a {@link P2p} activity. */
    final class P2pBuilder extends AmountBuilder<P2pBuilder> {

        private final String holdingId;

        P2pBuilder(ActivityType type, String holdingId) {
            super(type);
            this.holdingId = Validate.requireText(holdingId, "holdingId");
        }

        /**
         * Builds the activity.
         *
         * @return the activity to send
         * @throws NullPointerException if a required field is unset
         * @throws IllegalArgumentException if a field is out of range
         */
        public P2p build() {
            return new P2p(type, holdingId, amount, currency, datetime, tax, fee, description, broker, externalId);
        }
    }
}
