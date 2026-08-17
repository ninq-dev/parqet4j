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
import java.net.URI;
import java.util.Objects;

/**
 * A pending authorization-code flow: the URI to send the user to, plus the two secrets that must survive until the callback arrives.
 * <p>
 * Store the whole record against the browser session (or any keyed store) before redirecting — {@link ParqetOAuth#exchangeCode} needs
 * {@link #codeVerifier()} to complete PKCE and {@link #state()} to prove the callback belongs to this flow. {@link #toString()} is
 * redacted.
 *
 * @param authorizationUri where to send the user's browser
 * @param state the CSRF token echoed back on the callback
 * @param codeVerifier the PKCE verifier whose challenge went out in {@code authorizationUri}
 */
public record AuthorizationRequest(URI authorizationUri, String state, String codeVerifier) {

    /**
     * Canonical constructor.
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code state} or {@code codeVerifier} is blank
     */
    public AuthorizationRequest {
        Objects.requireNonNull(authorizationUri, "authorizationUri must not be null");
        Validate.requireText(state, "state");
        Validate.requireText(codeVerifier, "codeVerifier");
    }

    /**
     * Returns a redacted description that never contains the PKCE verifier.
     *
     * @return a safe-to-log summary
     */
    @Override
    public String toString() {
        return "AuthorizationRequest[state=" + state + ", codeVerifier=<redacted>, authorizationUri=" + authorizationUri + "]";
    }
}
