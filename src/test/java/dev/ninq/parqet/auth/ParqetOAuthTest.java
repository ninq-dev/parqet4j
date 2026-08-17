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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ninq.parqet.error.ParqetAuthException;
import dev.ninq.parqet.error.ParqetProtocolException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParqetOAuthTest {

    private static final URI REDIRECT = URI.create("https://example.com/callback");

    private StubAuthServer server;
    private ParqetOAuth oauth;

    @BeforeEach
    void startServer() {
        server = new StubAuthServer();
        oauth = ParqetOAuth.builder("client-1").redirectUri(REDIRECT).issuer(server.baseUri()).build();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void derivesTheEndpointsFromTheIssuer() {
        var production = ParqetOAuth.builder("client-1").redirectUri(REDIRECT).build();

        assertThat(production.issuer()).hasToString("https://connect.parqet.com");
        assertThat(production.authorizationEndpoint()).hasToString("https://connect.parqet.com/oauth2/authorize");
        assertThat(production.tokenEndpoint()).hasToString("https://connect.parqet.com/oauth2/token");
    }

    @Test
    void buildsAnAuthorizationUriWithAPkceS256Challenge() {
        var request = oauth.authorizationRequest(Set.of(Scope.PORTFOLIO_READ, Scope.PORTFOLIO_WRITE));
        var params = queryOf(request.authorizationUri());

        assertThat(params).containsEntry("client_id", "client-1")
                .containsEntry("redirect_uri", REDIRECT.toString())
                .containsEntry("response_type", "code")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("state", request.state());
        assertThat(params.get("scope")).contains("portfolio:read").contains("portfolio:write");

        // RFC 7636 §4.2: challenge = BASE64URL(SHA256(ASCII(verifier))), unpadded.
        assertThat(params.get("code_challenge")).isEqualTo(sha256Base64Url(request.codeVerifier()));
        assertThat(request.codeVerifier()).hasSize(43).doesNotContain("=", "+", "/");
    }

    @Test
    void issuesAFreshVerifierAndStatePerRequest() {
        var first = oauth.authorizationRequest(Set.of(Scope.PORTFOLIO_READ));
        var second = oauth.authorizationRequest(Set.of(Scope.PORTFOLIO_READ));

        assertThat(first.codeVerifier()).isNotEqualTo(second.codeVerifier());
        assertThat(first.state()).isNotEqualTo(second.state());
    }

    @Test
    void rejectsAnEmptyScopeSet() {
        assertThatThrownBy(() -> oauth.authorizationRequest(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one scope");
    }

    @Test
    void exchangesTheCodeAndSendsThePkceVerifier() {
        var request = oauth.authorizationRequest(Set.of(Scope.PORTFOLIO_READ));
        server.respondWith(200,
                """
                        {
                          "access_token": "at-1",
                          "token_type": "Bearer",
                          "refresh_token": "rt-1",
                          "expires_in": 3600,
                          "scope": "portfolio:read"
                        }
                        """);

        var tokens = oauth.exchangeCode(request, "the-code", request.state());

        assertThat(tokens.accessToken()).isEqualTo("at-1");
        assertThat(tokens.refreshTokenIfPresent()).contains("rt-1");
        assertThat(tokens.scopes()).containsExactly(Scope.PORTFOLIO_READ);
        assertThat(tokens.expiresAtIfKnown()).isPresent();
        assertThat(tokens.isExpired(Duration.ZERO)).isFalse();

        var form = formOf(server.lastBody());
        assertThat(form).containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "the-code")
                .containsEntry("redirect_uri", REDIRECT.toString())
                .containsEntry("code_verifier", request.codeVerifier())
                .containsEntry("client_id", "client-1")
                .doesNotContainKey("client_secret");
        assertThat(server.lastPath()).isEqualTo("/oauth2/token");
    }

    @Test
    void refusesACallbackWhoseStateDoesNotMatch() {
        var request = oauth.authorizationRequest(Set.of(Scope.PORTFOLIO_READ));

        assertThatThrownBy(() -> oauth.exchangeCode(request, "the-code", "someone-elses-state"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state mismatch");
        assertThat(server.requestCount()).isZero();
    }

    @Test
    void sendsTheClientSecretForConfidentialClients() {
        var confidential = ParqetOAuth.builder("client-1")
                .redirectUri(REDIRECT)
                .clientSecret("s3cret")
                .issuer(server.baseUri())
                .build();
        server.respondWith(200, "{\"access_token\": \"at-2\"}");

        confidential.refresh("rt-1");

        assertThat(formOf(server.lastBody()))
                .containsEntry("grant_type", "refresh_token")
                .containsEntry("refresh_token", "rt-1")
                .containsEntry("client_secret", "s3cret");
    }

    @Test
    void mapsARejectedGrantToAnAuthException() {
        server.respondWith(400, "{\"message\":\"invalid_grant\",\"statusCode\":400}");
        assertThatThrownBy(() -> oauth.refresh("revoked")).hasMessageContaining("invalid_grant");

        server.respondWith(401, "{\"message\":\"Unauthorized\"}");
        assertThatThrownBy(() -> oauth.refresh("revoked")).isInstanceOf(ParqetAuthException.class);
    }

    @Test
    void rejectsATokenResponseWithoutAnAccessToken() {
        server.respondWith(200, "{\"token_type\": \"Bearer\"}");

        assertThatThrownBy(() -> oauth.refresh("rt-1"))
                .isInstanceOf(ParqetProtocolException.class)
                .hasMessageContaining("no access_token");
    }

    @Test
    void refreshesProactivelyAndHandsTheNewTokensToTheCallback() {
        var persisted = new AtomicReference<Tokens>();
        var expired = new Tokens("stale", "Bearer", "rt-1", Instant.now().minusSeconds(5), "portfolio:read");
        var provider = TokenProvider.refreshing(oauth, expired, persisted::set);

        server.respondWith(200, "{\"access_token\": \"at-fresh\", \"expires_in\": 3600, \"refresh_token\": \"rt-2\"}");

        assertThat(provider.accessToken()).isEqualTo("at-fresh");
        assertThat(persisted.get().refreshToken()).isEqualTo("rt-2");
        assertThat(provider.currentTokens().accessToken()).isEqualTo("at-fresh");
    }

    @Test
    void keepsTheOldRefreshTokenWhenTheServerDoesNotRotateIt() {
        var tokens = new Tokens("at-1", "Bearer", "rt-1", Instant.now().plusSeconds(3600), null);
        var provider = TokenProvider.refreshing(oauth, tokens);

        server.respondWith(200, "{\"access_token\": \"at-2\", \"expires_in\": 3600}");

        assertThat(provider.refresh()).isTrue();
        assertThat(provider.currentTokens().refreshToken()).isEqualTo("rt-1");
        assertThat(provider.accessToken()).isEqualTo("at-2");
    }

    @Test
    void refusesToBuildARefreshingProviderWithoutARefreshToken() {
        var tokens = new Tokens("at-1", "Bearer", null, null, null);

        assertThatThrownBy(() -> TokenProvider.refreshing(oauth, tokens))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no refresh token");
    }

    @Test
    void keepsTokenMaterialOutOfToString() {
        var tokens = new Tokens("super-secret", "Bearer", "also-secret", Instant.EPOCH, "portfolio:read");
        var request = oauth.authorizationRequest(Set.of(Scope.PORTFOLIO_READ));

        assertThat(tokens).hasToString(
                "Tokens[tokenType=Bearer, scope=portfolio:read, expiresAt=1970-01-01T00:00:00Z, accessToken=<redacted>, "
                        + "refreshToken=<redacted>]");
        assertThat(request.toString()).doesNotContain(request.codeVerifier()).contains("<redacted>");
    }

    private static String sha256Base64Url(String verifier) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> queryOf(URI uri) {
        return formOf(uri.getRawQuery());
    }

    private static Map<String, String> formOf(String encoded) {
        var values = new LinkedHashMap<String, String>();
        Arrays.stream(encoded.split("&")).filter(p -> !p.isEmpty()).forEach(pair -> {
            var split = pair.indexOf('=');
            values.put(
                    URLDecoder.decode(pair.substring(0, split), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8));
        });
        return values;
    }
}
