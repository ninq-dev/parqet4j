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

import dev.ninq.parqet.internal.Validate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A booking on a holding, as returned by {@code GET /portfolios/{portfolioId}/activities}.
 * <p>
 * The API describes nine activity shapes, but they differ only in which {@link ActivityType} they carry and whether they report realized
 * gains — {@link ActivityType#SELL} and {@link ActivityType#TRANSFER_OUT} do, the other seven do not. This record therefore covers all of
 * them, with the four disposal fields left {@code null} where the API omits them.
 * <p>
 * All monetary values are expressed in the portfolio's currency. When the broker booked the activity in a different currency, {@link #fx()}
 * carries the original figures.
 *
 * @param id the activity id
 * @param type what the activity did
 * @param holdingId the holding the activity is booked on
 * @param holdingAssetType the asset type of that holding
 * @param asset how the API identifies the asset
 * @param currency the currency of every monetary value on this record
 * @param datetime when the activity was booked
 * @param shares the number of shares or units
 * @param price the price per share or unit
 * @param amount the gross amount, taxes and fees excluded
 * @param amountNet the net amount, taxes and fees included
 * @param tax the tax paid, {@code null} when the API omitted it
 * @param fee the fee paid, {@code null} when the API omitted it
 * @param description the user-visible description, {@code null} when unset
 * @param broker the broker the activity originated from, {@code null} when unset
 * @param externalId the identifier a previous write set on this activity, {@code null} when unset
 * @param fx the pre-conversion figures, {@code null} when the activity was booked in the portfolio currency
 * @param realizedGains the gain realized, gross; only on {@code SELL} and {@code TRANSFER_OUT}
 * @param realizedGainsNet the gain realized, net of taxes and fees; only on disposals
 * @param buyAmountNet what the disposed shares originally cost, net; only on disposals
 * @param avgHoldingPeriod the average holding period in days; only on disposals
 */
public record Activity(
        String id,
        ActivityType type,
        String holdingId,
        AssetType holdingAssetType,
        ActivityAsset asset,
        Currency currency,
        Instant datetime,
        BigDecimal shares,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal amountNet,
        BigDecimal tax,
        BigDecimal fee,
        String description,
        Broker broker,
        String externalId,
        FxInfo fx,
        BigDecimal realizedGains,
        BigDecimal realizedGainsNet,
        BigDecimal buyAmountNet,
        Double avgHoldingPeriod) {

    /**
     * Canonical constructor.
     *
     * @throws NullPointerException if any of {@code id}, {@code type}, {@code holdingId}, {@code holdingAssetType}, {@code asset},
     *             {@code currency}, or {@code datetime} is {@code null}
     */
    public Activity {
        Validate.requireText(id, "id");
        Objects.requireNonNull(type, "type must not be null");
        Validate.requireText(holdingId, "holdingId");
        Objects.requireNonNull(holdingAssetType, "holdingAssetType must not be null");
        Objects.requireNonNull(asset, "asset must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(datetime, "datetime must not be null");
    }

    /**
     * Returns whether this activity disposed of shares and therefore reports realized gains.
     *
     * @return {@code true} for {@link ActivityType#SELL} and {@link ActivityType#TRANSFER_OUT}
     */
    public boolean isDisposal() {
        return type == ActivityType.SELL || type == ActivityType.TRANSFER_OUT;
    }

    /**
     * Returns the tax paid on this activity.
     *
     * @return the tax, empty when the API omitted it
     */
    public Optional<BigDecimal> taxIfPresent() {
        return Optional.ofNullable(tax);
    }

    /**
     * Returns the fee paid on this activity.
     *
     * @return the fee, empty when the API omitted it
     */
    public Optional<BigDecimal> feeIfPresent() {
        return Optional.ofNullable(fee);
    }

    /**
     * Returns the user-visible description.
     *
     * @return the description, empty when unset
     */
    public Optional<String> descriptionIfSet() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the broker the activity originated from.
     *
     * @return the broker, empty when unset
     */
    public Optional<Broker> brokerIfSet() {
        return Optional.ofNullable(broker);
    }

    /**
     * Returns the identifier a previous write set on this activity.
     *
     * @return the external id, empty when unset
     */
    public Optional<String> externalIdIfSet() {
        return Optional.ofNullable(externalId);
    }

    /**
     * Returns the pre-conversion figures for a foreign-currency activity.
     *
     * @return the FX detail, empty when the activity was booked in the portfolio currency
     */
    public Optional<FxInfo> fxIfPresent() {
        return Optional.ofNullable(fx);
    }

    /**
     * Returns the gross gain realized by this activity.
     *
     * @return the realized gain, empty unless this is a disposal
     */
    public Optional<BigDecimal> realizedGainsIfPresent() {
        return Optional.ofNullable(realizedGains);
    }

    /**
     * Returns the average holding period of the disposed shares.
     *
     * @return the holding period in days, empty unless this is a disposal
     */
    public OptionalDouble avgHoldingPeriodIfPresent() {
        return avgHoldingPeriod == null ? OptionalDouble.empty() : OptionalDouble.of(avgHoldingPeriod);
    }
}
