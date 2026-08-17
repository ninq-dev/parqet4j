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

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ninq.parqet.error.ParqetApiException;
import dev.ninq.parqet.error.ParqetProtocolException;
import dev.ninq.parqet.error.ParqetTransportException;
import dev.ninq.parqet.internal.ParqetJson;
import dev.ninq.parqet.internal.Validate;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The OAuth 2.0 authorization-code flow with PKCE, as Parqet Connect implements it.
 * <p>
 * The library covers the two server-side halves of the flow — building the authorization URI and talking to the token endpoint. Serving the
 * redirect URI and keeping the user's session is the application's job.
 *
 * <pre>{@code
 * var oauth = ParqetOAuth.builder(clientId)
 *         .redirectUri(URI.create("https://example.com/callback"))
 *         .build();
 *
 * // 1. before redirecting: create the request and stash it against the session
 * var request = oauth.authorizationRequest(Set.of(Scope.PORTFOLIO_READ));
 * session.put("parqet.oauth", request);
 * redirect(request.authorizationUri());
 *
 * // 2. in the callback handler
 * var tokens = oauth.exchangeCode(session.get("parqet.oauth"), code, state);
 * }</pre>
 * <p>
 * Instances are immutable and safe to share across threads.
 */
public final class ParqetOAuth {

    /** The default issuer, {@code https://connect.parqet.com}. */
    public static final URI DEFAULT_ISSUER = URI.create("https://connect.parqet.com");

    private static final Logger LOG = LoggerFactory.getLogger(ParqetOAuth.class);

    /** RFC 7636 recommends 32 bytes of entropy, which base64url-encodes to 43 characters. */
    private static final int CODE_VERIFIER_BYTES = 32;

    private static final int STATE_BYTES = 16;

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final String clientId;
    private final String clientSecret;
    private final URI redirectUri;
    private final URI issuer;
    private final URI authorizationEndpoint;
    private final URI tokenEndpoint;
    private final HttpClient http;
    private final Duration requestTimeout;
    private final SecureRandom random = new SecureRandom();

    private ParqetOAuth(Builder builder) {
        this.clientId = builder.clientId;
        this.clientSecret = builder.clientSecret;
        this.redirectUri = Objects.requireNonNull(builder.redirectUri, "redirectUri must be set");
        this.issuer = builder.issuer;
        this.authorizationEndpoint = resolve(builder.issuer, "/oauth2/authorize");
        this.tokenEndpoint = resolve(builder.issuer, "/oauth2/token");
        this.http = builder.http != null ? builder.http : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.requestTimeout = builder.requestTimeout;
    }

    private static URI resolve(URI issuer, String path) {
        var base = issuer.toString();
        return URI.create(base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path);
    }

    /**
     * Starts building a client for the given integration.
     *
     * @param clientId the Client ID from the Parqet Developer Console
     * @return a new builder
     * @throws NullPointerException if {@code clientId} is {@code null}
     * @throws IllegalArgumentException if {@code clientId} is blank
     */
    public static Builder builder(String clientId) {
        return new Builder(clientId);
    }

    /**
     * Creates an authorization request: a fresh PKCE verifier, a fresh state, and the URI carrying their public halves.
     *
     * @param scopes the scopes to request; must not be empty
     * @return the request to store and redirect with
     * @throws IllegalArgumentException if {@code scopes} is empty
     */
    public AuthorizationRequest authorizationRequest(Set<Scope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("at least one scope must be requested");
        }
        var codeVerifier = randomToken(CODE_VERIFIER_BYTES);
        var state = randomToken(STATE_BYTES);

        var params = new LinkedHashMap<String, String>();
        params.put("client_id", clientId);
        params.put("redirect_uri", redirectUri.toString());
        params.put("response_type", "code");
        params.put("scope", scopes.stream().map(Scope::value).collect(Collectors.joining(" ")));
        params.put("code_challenge", codeChallenge(codeVerifier));
        params.put("code_challenge_method", "S256");
        params.put("state", state);

        var uri = URI.create(authorizationEndpoint + "?" + formEncode(params));
        LOG.debug("Built authorization request for scopes {}", params.get("scope"));
        return new AuthorizationRequest(uri, state, codeVerifier);
    }

    /**
     * Exchanges an authorization code for tokens, after checking that the callback's {@code state} matches the one this flow issued.
     *
     * @param request the request returned by {@link #authorizationRequest}
     * @param code the {@code code} query parameter from the callback
     * @param returnedState the {@code state} query parameter from the callback
     * @return the granted tokens
     * @throws IllegalArgumentException if {@code returnedState} does not match the request
     * @throws ParqetApiException if the token endpoint rejects the exchange
     * @throws ParqetTransportException if the token endpoint cannot be reached
     */
    public Tokens exchangeCode(AuthorizationRequest request, String code, String returnedState) {
        Objects.requireNonNull(request, "request must not be null");
        Validate.requireText(code, "code");
        if (!MessageDigest.isEqual(
                request.state().getBytes(StandardCharsets.UTF_8),
                String.valueOf(returnedState).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("OAuth state mismatch: the callback does not belong to this authorization request");
        }
        var form = new LinkedHashMap<String, String>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri.toString());
        form.put("code_verifier", request.codeVerifier());
        return token(form);
    }

    /**
     * Exchanges a refresh token for a new access token.
     *
     * @param refreshToken the refresh token from a previous grant
     * @return the new tokens; the refresh token may itself be rotated
     * @throws ParqetApiException if the token endpoint rejects the refresh, typically because the token was revoked
     * @throws ParqetTransportException if the token endpoint cannot be reached
     */
    public Tokens refresh(String refreshToken) {
        Validate.requireText(refreshToken, "refreshToken");
        var form = new LinkedHashMap<String, String>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        return token(form);
    }

    private Tokens token(Map<String, String> form) {
        form.put("client_id", clientId);
        if (clientSecret != null) {
            form.put("client_secret", clientSecret);
        }
        var request = HttpRequest.newBuilder(tokenEndpoint)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ParqetTransportException("Token request to " + tokenEndpoint + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ParqetTransportException("Token request to " + tokenEndpoint + " was interrupted", e);
        }

        if (response.statusCode() / 100 != 2) {
            var error = ParqetJson.readErrorLeniently(response.body());
            LOG.debug("Token request failed with HTTP {} ({})", response.statusCode(), error.message());
            throw ParqetApiException.of(
                    response.statusCode(),
                    error.message(),
                    error.error(),
                    response.headers().firstValue("cf-ray").orElse(null),
                    null);
        }

        var wire = ParqetJson.read(response.body(), TokenResponse.class);
        if (wire.accessToken() == null || wire.accessToken().isBlank()) {
            throw new ParqetProtocolException("Token endpoint returned no access_token");
        }
        var expiresAt = wire.expiresIn() == null ? null : Instant.now().plusSeconds(wire.expiresIn());
        LOG.debug("Obtained tokens for grant_type={}, expiresAt={}", form.get("grant_type"), expiresAt);
        return new Tokens(wire.accessToken(), wire.tokenType(), wire.refreshToken(), expiresAt, wire.scope());
    }

    /**
     * Returns the issuer this client talks to.
     *
     * @return the issuer URI
     */
    public URI issuer() {
        return issuer;
    }

    /**
     * Returns the authorization endpoint derived from the issuer.
     *
     * @return the authorization endpoint URI
     */
    public URI authorizationEndpoint() {
        return authorizationEndpoint;
    }

    /**
     * Returns the token endpoint derived from the issuer.
     *
     * @return the token endpoint URI
     */
    public URI tokenEndpoint() {
        return tokenEndpoint;
    }

    private String randomToken(int bytes) {
        var buffer = new byte[bytes];
        random.nextBytes(buffer);
        return BASE64_URL.encodeToString(buffer);
    }

    private static String codeChallenge(String codeVerifier) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return BASE64_URL.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform but is unavailable", e);
        }
    }

    private static String formEncode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("scope") String scope) {
    }

    /** Builder for {@link ParqetOAuth}. */
    public static final class Builder {

        private final String clientId;
        private String clientSecret;
        private URI redirectUri;
        private URI issuer = DEFAULT_ISSUER;
        private HttpClient http;
        private Duration requestTimeout = Duration.ofSeconds(30);

        private Builder(String clientId) {
            this.clientId = Validate.requireText(clientId, "clientId");
        }

        /**
         * Sets the redirect URI. It must be one of the URIs registered for the integration, and is sent again on the code exchange.
         *
         * @param redirectUri the registered redirect URI
         * @return this builder
         */
        public Builder redirectUri(URI redirectUri) {
            this.redirectUri = Objects.requireNonNull(redirectUri, "redirectUri must not be null");
            return this;
        }

        /**
         * Sets the client secret for confidential clients. Public clients — anything that cannot keep a secret, such as a desktop or mobile app —
         * leave this unset and rely on PKCE alone.
         *
         * @param clientSecret the client secret
         * @return this builder
         */
        public Builder clientSecret(String clientSecret) {
            this.clientSecret = Validate.requireText(clientSecret, "clientSecret");
            return this;
        }

        /**
         * Overrides the issuer. Only useful for tests against a stub authorization server.
         *
         * @param issuer the issuer URI; the authorization and token endpoints are derived from it
         * @return this builder
         */
        public Builder issuer(URI issuer) {
            this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
            return this;
        }

        /**
         * Supplies the HTTP client used for token requests. The caller keeps ownership; it is never closed by this class.
         *
         * @param http the HTTP client
         * @return this builder
         */
        public Builder httpClient(HttpClient http) {
            this.http = Objects.requireNonNull(http, "http must not be null");
            return this;
        }

        /**
         * Sets the per-request timeout for token calls.
         *
         * @param requestTimeout the timeout; must be positive
         * @return this builder
         */
        public Builder requestTimeout(Duration requestTimeout) {
            Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
            if (requestTimeout.isNegative() || requestTimeout.isZero()) {
                throw new IllegalArgumentException("requestTimeout must be positive, was " + requestTimeout);
            }
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * Builds the client.
         *
         * @return a new {@code ParqetOAuth}
         * @throws NullPointerException if no redirect URI was set
         */
        public ParqetOAuth build() {
            return new ParqetOAuth(this);
        }
    }
}
