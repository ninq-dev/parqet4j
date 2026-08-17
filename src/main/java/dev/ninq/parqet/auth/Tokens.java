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

import dev.ninq.parqet.internal.Validate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The credentials returned by the Parqet token endpoint.
 * <p>
 * Persist these — the refresh token is what keeps an integration working after the access token expires. {@link #toString()} is redacted so
 * tokens do not leak into logs.
 *
 * @param accessToken the bearer token to send on API calls
 * @param tokenType the token type, {@code Bearer} in practice
 * @param refreshToken the token that buys a new access token, {@code null} if the server sent none
 * @param expiresAt when {@code accessToken} stops working, {@code null} if the server sent no {@code expires_in}
 * @param scope the space-separated scopes actually granted, {@code null} if the server sent none
 */
public record Tokens(String accessToken, String tokenType, String refreshToken, Instant expiresAt, String scope) {

    /**
     * Canonical constructor.
     *
     * @throws NullPointerException if {@code accessToken} is {@code null}
     * @throws IllegalArgumentException if {@code accessToken} is blank
     */
    public Tokens {
        Validate.requireText(accessToken, "accessToken");
    }

    /**
     * Returns the refresh token.
     *
     * @return the refresh token, empty when the server sent none
     */
    public Optional<String> refreshTokenIfPresent() {
        return Optional.ofNullable(refreshToken);
    }

    /**
     * Returns the access token's expiry.
     *
     * @return the expiry instant, empty when the server sent no lifetime
     */
    public Optional<Instant> expiresAtIfKnown() {
        return Optional.ofNullable(expiresAt);
    }

    /**
     * Returns the granted scopes this client recognises; unknown scope strings are skipped.
     *
     * @return the granted scopes, in the order the server listed them
     */
    public Set<Scope> scopes() {
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        var granted = new LinkedHashSet<Scope>();
        Arrays.stream(scope.split(" ")).map(Scope::fromValue).flatMap(Optional::stream).forEach(granted::add);
        return Set.copyOf(granted);
    }

    /**
     * Returns whether the access token is expired, or close enough that it should be refreshed now.
     *
     * @param skew how far ahead of the real expiry to consider the token stale
     * @return {@code true} if the token expires within {@code skew}; {@code false} when the expiry is unknown
     */
    public boolean isExpired(Duration skew) {
        return expiresAt != null && Instant.now().plus(skew).isAfter(expiresAt);
    }

    /**
     * Returns a redacted description that never contains token material.
     *
     * @return a safe-to-log summary
     */
    @Override
    public String toString() {
        return "Tokens[tokenType=" + tokenType + ", scope=" + scope + ", expiresAt=" + expiresAt + ", accessToken=<redacted>, refreshToken="
                + (refreshToken == null ? "<none>" : "<redacted>") + "]";
    }
}
