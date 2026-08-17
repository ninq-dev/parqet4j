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
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Supplies the bearer token for each API call, and — for the refreshing implementation — renews it when it goes stale.
 * <p>
 * Implementations must be safe for concurrent use: one client can be shared across threads.
 */
public interface TokenProvider {

    /**
     * Returns the access token to send on the next request.
     *
     * @return a bearer token, never {@code null} or blank
     */
    String accessToken();

    /**
     * Asks the provider to obtain a fresh access token after the API rejected the current one.
     * <p>
     * The client calls this at most once per request. Returning {@code false} — the default — lets the {@code 401} surface to the caller
     * unchanged.
     *
     * @return {@code true} if {@link #accessToken()} will now return a different token
     */
    default boolean refresh() {
        return false;
    }

    /**
     * Returns a provider that always hands out the same token.
     * <p>
     * Use this when the application manages OAuth itself, or for a short-lived script.
     *
     * @param accessToken the bearer token
     * @return a provider wrapping {@code accessToken}
     * @throws NullPointerException if {@code accessToken} is {@code null}
     * @throws IllegalArgumentException if {@code accessToken} is blank
     */
    static TokenProvider of(String accessToken) {
        Validate.requireText(accessToken, "accessToken");
        return () -> accessToken;
    }

    /**
     * Returns a provider that keeps a grant alive by refreshing it.
     * <p>
     * It renews the token when it is within {@value RefreshingTokenProvider#SKEW_SECONDS} seconds of expiry, and again if the API answers
     * {@code 401} anyway. Every new {@link Tokens} is handed to {@code onRefresh} before it is used, so an application can persist the rotated
     * refresh token; if that callback throws, the refresh fails rather than silently losing the new token.
     *
     * @param oauth the OAuth client to refresh through
     * @param initial the tokens obtained from the authorization-code exchange
     * @param onRefresh called with each renewed set of tokens; use it to persist them
     * @return a refreshing provider
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code initial} carries no refresh token
     */
    static TokenProvider refreshing(ParqetOAuth oauth, Tokens initial, Consumer<Tokens> onRefresh) {
        return new RefreshingTokenProvider(oauth, initial, onRefresh);
    }

    /**
     * Returns a provider that refreshes but does not report renewed tokens anywhere.
     * <p>
     * Suitable for a process that will not outlive the grant. Anything longer-lived should use
     * {@link #refreshing(ParqetOAuth, Tokens, Consumer)} and persist the rotated refresh token.
     *
     * @param oauth the OAuth client to refresh through
     * @param initial the tokens obtained from the authorization-code exchange
     * @return a refreshing provider
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code initial} carries no refresh token
     */
    static TokenProvider refreshing(ParqetOAuth oauth, Tokens initial) {
        return refreshing(oauth, initial, tokens -> {
        });
    }

    /**
     * Returns the tokens this provider currently holds, if it tracks any.
     *
     * @return the current tokens, or {@code null} for providers that hold only a bare access token
     */
    default Tokens currentTokens() {
        return null;
    }

    /**
     * Skew applied when deciding whether a token is close enough to expiry to renew.
     *
     * @return the refresh skew
     */
    static Duration refreshSkew() {
        return Duration.ofSeconds(RefreshingTokenProvider.SKEW_SECONDS);
    }

    /**
     * Requires that the given tokens can actually be refreshed.
     *
     * @param tokens the tokens to check
     * @return {@code tokens}
     * @throws NullPointerException if {@code tokens} is {@code null}
     * @throws IllegalArgumentException if {@code tokens} carries no refresh token
     */
    static Tokens requireRefreshable(Tokens tokens) {
        Objects.requireNonNull(tokens, "tokens must not be null");
        if (tokens.refreshToken() == null) {
            throw new IllegalArgumentException("tokens carry no refresh token and cannot be refreshed");
        }
        return tokens;
    }
}
