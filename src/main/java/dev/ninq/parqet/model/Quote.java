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

/**
 * A price for a user-managed asset at a point in time.
 * <p>
 * Custom holdings have no market price, so their value comes from quotes the integration pushes — either alongside the holding when it is
 * created, or later through {@link QuoteUpdate}.
 *
 * @param currency the currency {@code price} is expressed in
 * @param datetime when the price was valid
 * @param price the price of one unit
 */
public record Quote(Currency currency, Instant datetime, BigDecimal price) {

    /**
     * Canonical constructor.
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public Quote {
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(datetime, "datetime must not be null");
        Validate.requireAmount(price, "price");
    }
}
