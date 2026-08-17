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
package dev.ninq.parqet.auth;

import java.util.Optional;
import java.util.stream.Stream;

/** The OAuth scopes a Parqet Connect integration can request. */
public enum Scope {

    /** Read access to portfolio data. */
    PORTFOLIO_READ("portfolio:read"),

    /** Write access to portfolio data — creating portfolios, holdings, activities and quotes. */
    PORTFOLIO_WRITE("portfolio:write");

    private final String value;

    Scope(String value) {
        this.value = value;
    }

    /**
     * Returns the wire representation of this scope.
     *
     * @return the scope string as it appears in an authorization request
     */
    public String value() {
        return value;
    }

    /**
     * Maps a scope string from a token response back to a constant.
     *
     * @param value the scope string
     * @return the matching constant, empty if this client does not know the scope
     */
    public static Optional<Scope> fromValue(String value) {
        return Stream.of(values()).filter(s -> s.value.equals(value)).findFirst();
    }
}
