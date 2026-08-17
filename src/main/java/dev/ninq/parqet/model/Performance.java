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
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * How a portfolio or a single holding did over the requested interval.
 * <p>
 * The API nests most figures one level deeper, under {@code inInterval} — the valuations being the exception, which sit directly on
 * {@code valuation}. That nesting is flattened away here: this record is the same data with one level less ceremony. Every monetary figure
 * is expressed in the currency the request asked for.
 *
 * @param xirr the (irregular) internal rate of return, or {@code null} when it cannot be computed
 * @param ttwror the true time-weighted rate of return, or {@code null} when it cannot be computed
 * @param fees the fees paid during the interval
 * @param taxes the taxes paid during the interval
 * @param unrealizedGains the change in value of positions still held
 * @param realizedGains the gains locked in by disposals during the interval
 * @param dividends the dividends received during the interval, or {@code null} when the API reported none
 * @param valuationAtIntervalStart what the selection was worth when the interval opened
 * @param valuationAtIntervalEnd what the selection was worth when the interval closed
 */
public record Performance(Double xirr, Double ttwror, BigDecimal fees, BigDecimal taxes, Gains unrealizedGains, Gains realizedGains,
        Dividends dividends, BigDecimal valuationAtIntervalStart, BigDecimal valuationAtIntervalEnd) {

    /**
     * Returns the internal rate of return.
     *
     * @return the XIRR, empty when the API could not compute one
     */
    public OptionalDouble xirrIfPresent() {
        return xirr == null ? OptionalDouble.empty() : OptionalDouble.of(xirr);
    }

    /**
     * Returns the true time-weighted rate of return, the measure that strips out the effect of deposits and withdrawals.
     *
     * @return the TTWROR, empty when the API could not compute one
     */
    public OptionalDouble ttwrorIfPresent() {
        return ttwror == null ? OptionalDouble.empty() : OptionalDouble.of(ttwror);
    }

    /**
     * Returns the dividends received during the interval.
     *
     * @return the dividend summary, empty when the API reported none
     */
    public Optional<Dividends> dividendsIfPresent() {
        return Optional.ofNullable(dividends);
    }

    /**
     * Returns the change in valuation across the interval.
     *
     * @return {@code valuationAtIntervalEnd} minus {@code valuationAtIntervalStart}
     */
    public BigDecimal valuationChange() {
        return valuationAtIntervalEnd.subtract(valuationAtIntervalStart);
    }

    @JsonCreator
    static Performance fromJson(
            @JsonProperty("kpis") KpisWire kpis,
            @JsonProperty("fees") FeesWire fees,
            @JsonProperty("taxes") TaxesWire taxes,
            @JsonProperty("unrealizedGains") GainsWire unrealizedGains,
            @JsonProperty("realizedGains") GainsWire realizedGains,
            @JsonProperty("dividends") DividendsWire dividends,
            @JsonProperty("valuation") ValuationValues valuation) {
        var kpiValues = kpis == null ? null : kpis.inInterval();
        return new Performance(
                kpiValues == null ? null : kpiValues.xirr(),
                kpiValues == null ? null : kpiValues.ttwror(),
                fees == null || fees.inInterval() == null ? null : fees.inInterval().fees(),
                taxes == null || taxes.inInterval() == null ? null : taxes.inInterval().taxes(),
                unrealizedGains == null ? null : unrealizedGains.inInterval(),
                realizedGains == null ? null : realizedGains.inInterval(),
                dividends == null ? null : dividends.inInterval(),
                valuation == null ? null : valuation.atIntervalStart(),
                valuation == null ? null : valuation.atIntervalEnd());
    }

    /**
     * A gross and net gain, in absolute terms and as a return on the capital that produced it.
     *
     * @param gainGross the gain excluding taxes and fees
     * @param gainNet the gain including taxes and fees
     * @param returnGross {@code gainGross} relative to the capital invested
     * @param returnNet {@code gainNet} relative to the capital invested
     */
    public record Gains(BigDecimal gainGross, BigDecimal gainNet, double returnGross, double returnNet) {
    }

    /**
     * Dividends received during the interval, and what was withheld from them.
     *
     * @param gainGross the dividends excluding taxes and fees
     * @param gainNet the dividends including taxes and fees
     * @param taxes the taxes paid on those dividends
     * @param fees the fees paid on those dividends
     */
    public record Dividends(BigDecimal gainGross, BigDecimal gainNet, BigDecimal taxes, BigDecimal fees) {
    }

    record KpisWire(KpiValues inInterval) {
    }

    record KpiValues(Double xirr, Double ttwror) {
    }

    record FeesWire(FeeValues inInterval) {
    }

    record FeeValues(BigDecimal fees) {
    }

    record TaxesWire(TaxValues inInterval) {
    }

    record TaxValues(BigDecimal taxes) {
    }

    record GainsWire(Gains inInterval) {
    }

    record DividendsWire(Dividends inInterval) {
    }

    record ValuationValues(BigDecimal atIntervalStart, BigDecimal atIntervalEnd) {
    }
}
