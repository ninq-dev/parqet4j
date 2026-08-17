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
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A Parqet portfolio, as returned by {@code GET /portfolios}.
 * <p>
 * Only the portfolios the user shared with this integration are listed.
 *
 * @param id the portfolio id, used as the path parameter of every per-portfolio call
 * @param currency the portfolio's base currency
 * @param name the display name
 * @param createdAt when the portfolio was created
 * @param distinctBrokers the broker identifiers that contributed data, never {@code null}
 * @param subAccounts the portfolio's sub-accounts, never {@code null}
 */
public record Portfolio(
        String id,
        Currency currency,
        String name,
        Instant createdAt,
        List<String> distinctBrokers,
        List<SubAccount> subAccounts) {

    /**
     * Canonical constructor.
     *
     * @throws NullPointerException if {@code id}, {@code currency}, {@code name}, or {@code createdAt} is {@code null}
     */
    public Portfolio {
        Validate.requireText(id, "id");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        distinctBrokers = Validate.copyOf(distinctBrokers);
        subAccounts = Validate.copyOf(subAccounts);
    }

    /**
     * Returns the portfolio's default sub-account.
     *
     * @return the default sub-account
     * @throws IllegalStateException if the API listed none, which should not happen
     */
    public SubAccount defaultSubAccount() {
        return subAccounts.stream()
                .filter(SubAccount::isDefault)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Portfolio " + id + " has no default sub-account"));
    }
}
