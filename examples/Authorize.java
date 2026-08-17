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

import com.sun.net.httpserver.HttpServer;
import dev.ninq.parqet.auth.ParqetOAuth;
import dev.ninq.parqet.auth.Scope;
import dev.ninq.parqet.auth.Tokens;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 01-authorize — walk the OAuth 2.0 authorization-code flow with PKCE and print the tokens.
 *
 * <p>Register an integration at https://developer.parqet.com, add {@code http://localhost:1337/callback}
 * as a redirect URL, and pass the Client ID:
 *
 * <pre>{@code examples/run.sh Authorize <client-id> [--write]}</pre>
 *
 * <p>The other examples read the resulting access token from {@code PARQET_ACCESS_TOKEN}.
 */
public final class Authorize {

    private static final int PORT = 1337;
    private static final URI REDIRECT = URI.create("http://localhost:" + PORT + "/callback");

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: Authorize <client-id> [--write]");
            System.exit(2);
        }
        var scopes = Arrays.asList(args).contains("--write")
                ? Set.of(Scope.PORTFOLIO_READ, Scope.PORTFOLIO_WRITE)
                : Set.of(Scope.PORTFOLIO_READ);

        var oauth = ParqetOAuth.builder(args[0]).redirectUri(REDIRECT).build();
        var request = oauth.authorizationRequest(scopes);

        // In a real application this callback is a route in your web app, and the request is stashed
        // against the user's session rather than held in a local variable.
        var callback = new CompletableFuture<Map<String, String>>();
        var server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/callback", exchange -> {
            var params = queryOf(exchange.getRequestURI().getRawQuery());
            var body = (params.containsKey("code") ? "Authorized. You can close this tab." : "Authorization failed.")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (exchange) {
                exchange.getResponseBody().write(body);
            }
            callback.complete(params);
        });
        server.start();

        System.out.println("Open this URL and approve access:\n");
        System.out.println("  " + request.authorizationUri());
        System.out.println("\nWaiting for the callback on " + REDIRECT + " …");

        var params = callback.join();
        server.stop(0);

        if (params.containsKey("error")) {
            System.err.println("Authorization was denied: " + params.get("error"));
            System.exit(1);
        }

        Tokens tokens = oauth.exchangeCode(request, params.get("code"), params.get("state"));
        System.out.println("\nGranted scopes: " + tokens.scopes());
        System.out.println("Expires at:     " + tokens.expiresAtIfKnown().orElse(null));
        System.out.println("\nexport PARQET_ACCESS_TOKEN=" + tokens.accessToken());
        tokens.refreshTokenIfPresent()
                .ifPresent(refresh -> System.out.println("export PARQET_REFRESH_TOKEN=" + refresh));
        System.out.println("\nKeep the refresh token safe — it is what keeps the integration working.");
    }

    private static Map<String, String> queryOf(String raw) {
        var params = new HashMap<String, String>();
        if (raw == null) {
            return params;
        }
        for (var pair : raw.split("&")) {
            var split = pair.indexOf('=');
            if (split > 0) {
                params.put(
                        URLDecoder.decode(pair.substring(0, split), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }
}
