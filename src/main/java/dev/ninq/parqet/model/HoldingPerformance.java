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

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One holding's contribution to a {@link PortfolioPerformance}: what it is, what it is worth, and how it did.
 *
 * @param id the holding id
 * @param activityCount how many activities are booked on this holding
 * @param logo a logo image for the asset, or {@code null}
 * @param nickname the user's name for this holding, or {@code null}
 * @param asset what the holding is invested in
 * @param externalId the identifier a previous write set on this holding, or {@code null}
 * @param subAccount the sub-account the holding lives in
 * @param currency the currency the holding is denominated in
 * @param startQuote the price at the start of the interval
 * @param quote the price at the end of the interval
 * @param earliestActivityDate the date of the oldest activity on this holding
 * @param performance how the holding did over the interval
 * @param position the size and value of the position
 */
public record HoldingPerformance(String id, int activityCount, URI logo, String nickname, HoldingAsset asset, String externalId,
        String subAccount, Currency currency, MarketQuote startQuote, MarketQuote quote, LocalDate earliestActivityDate,
        Performance performance, Position position) {

    /**
     * Returns the asset type of this holding — a shorthand for {@code asset().type()}.
     *
     * @return the asset type
     */
    public AssetType assetType() {
        return asset.type();
    }

    /**
     * A price for the asset at a point in time.
     *
     * @param currency the currency {@code price} is expressed in
     * @param exchange the venue the price came from
     * @param datetime when the price was observed
     * @param price the price of one unit
     * @param fx the conversion applied to reach {@code currency}, or {@code null} when none was needed
     */
    public record MarketQuote(Currency currency, String exchange, Instant datetime, BigDecimal price, Fx fx) {

        /**
         * The conversion applied to a quote.
         *
         * @param rate the exchange rate used
         * @param originalCurrency the currency the venue quoted in
         */
        public record Fx(BigDecimal rate, Currency originalCurrency) {
        }
    }

    /**
     * The size and value of a position.
     *
     * @param shares how many shares or units are held
     * @param purchasePrice the average price paid per share
     * @param purchaseValue the total paid for the position
     * @param currentPrice the price per share now
     * @param currentValue what the position is worth now
     * @param isSold whether every share has been sold
     */
    public record Position(BigDecimal shares, BigDecimal purchasePrice, BigDecimal purchaseValue, BigDecimal currentPrice,
            BigDecimal currentValue, boolean isSold) {

        /**
         * Returns the unrealized gain on this position.
         *
         * @return {@code currentValue} minus {@code purchaseValue}
         */
        public BigDecimal unrealizedGain() {
            return currentValue.subtract(purchaseValue);
        }
    }
}
