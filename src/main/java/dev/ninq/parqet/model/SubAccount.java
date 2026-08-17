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
import java.util.Optional;

/**
 * A securities account a broker manages inside one Parqet portfolio — for example two depots at the same bank imported through a single
 * brokerage connection.
 * <p>
 * Every portfolio has exactly one default sub-account, which carries no name. Named sub-accounts appear alongside it.
 *
 * @param id the sub-account identifier, of the form {@code portfolioId::subAccountId}
 * @param name the display name, {@code null} for the default sub-account
 * @param isDefault whether this is the portfolio's default sub-account
 */
public record SubAccount(String id, String name, boolean isDefault) {

    /**
     * Canonical constructor.
     *
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} does not match {@code ^\w+::\w+$}
     */
    public SubAccount {
        Validate.requireSubAccountId(id);
    }

    /**
     * Returns the display name of this sub-account.
     *
     * @return the name, empty for the default sub-account
     */
    public Optional<String> displayName() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the id of the portfolio this sub-account belongs to.
     *
     * @return the portfolio id — the part of {@link #id()} before {@code ::}
     */
    public String portfolioId() {
        return id.substring(0, id.indexOf("::"));
    }
}
