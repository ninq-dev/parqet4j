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
import java.util.Objects;

/**
 * The original, pre-conversion figures of an activity that was executed in a currency other than the portfolio's.
 * <p>
 * The {@link Activity} itself always reports amounts in the portfolio currency; this record keeps what the broker actually booked.
 *
 * @param rate the exchange rate applied, expressed as portfolio currency per original currency
 * @param originalCurrency the currency the activity was executed in
 * @param originalPrice the per-share price in {@code originalCurrency}
 * @param originalTax the tax in {@code originalCurrency}
 * @param originalFee the fee in {@code originalCurrency}
 * @param originalAmount the gross amount in {@code originalCurrency}
 * @param originalAmountNet the net amount in {@code originalCurrency}
 */
public record FxInfo(
        BigDecimal rate,
        Currency originalCurrency,
        BigDecimal originalPrice,
        BigDecimal originalTax,
        BigDecimal originalFee,
        BigDecimal originalAmount,
        BigDecimal originalAmountNet) {

    /**
     * Canonical constructor.
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public FxInfo {
        Validate.requireAmount(rate, "rate");
        Objects.requireNonNull(originalCurrency, "originalCurrency must not be null");
        Validate.requireAmount(originalPrice, "originalPrice");
        Validate.requireAmount(originalTax, "originalTax");
        Validate.requireAmount(originalFee, "originalFee");
        Validate.requireAmount(originalAmount, "originalAmount");
        Validate.requireAmount(originalAmountNet, "originalAmountNet");
    }
}
