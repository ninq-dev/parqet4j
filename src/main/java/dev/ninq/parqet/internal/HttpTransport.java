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
package dev.ninq.parqet.internal;

import dev.ninq.parqet.RetryPolicy;
import dev.ninq.parqet.auth.TokenProvider;
import dev.ninq.parqet.error.ParqetApiException;
import dev.ninq.parqet.error.ParqetTransportException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything between a typed call and the wire: the bearer header, retries, and the mapping from status codes to exceptions.
 * <p>
 * Calls are blocking. Nothing here uses {@code synchronized} around I/O, so a caller may drive many requests from virtual threads without
 * pinning carriers.
 */
public final class HttpTransport implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HttpTransport.class);

    private final URI baseUri;
    private final HttpClient http;
    private final boolean ownsClient;
    private final Duration requestTimeout;
    private final TokenProvider tokens;
    private final RetryPolicy retry;
    private final String userAgent;

    /**
     * Creates a transport.
     *
     * @param baseUri the API root, without a trailing slash
     * @param http the HTTP client to send through
     * @param ownsClient whether {@link #close()} should close {@code http}
     * @param requestTimeout the per-request timeout
     * @param tokens where the bearer token comes from
     * @param retry when to re-issue a failed request
     * @param userAgent the {@code User-Agent} to send
     */
    public HttpTransport(URI baseUri, HttpClient http, boolean ownsClient, Duration requestTimeout, TokenProvider tokens, RetryPolicy retry,
            String userAgent) {
        this.baseUri = baseUri;
        this.http = http;
        this.ownsClient = ownsClient;
        this.requestTimeout = requestTimeout;
        this.tokens = tokens;
        this.retry = retry;
        this.userAgent = userAgent;
    }

    /**
     * Issues a {@code GET} and parses the response.
     *
     * @param <T> the response model type
     * @param path the path below the base URI, starting with a slash
     * @param query the query parameters, in order; repeated keys become repeated parameters
     * @param type the response model class
     * @return the parsed response
     */
    public <T> T get(String path, List<Map.Entry<String, String>> query, Class<T> type) {
        return ParqetJson.read(send("GET", path, query, null, true), type);
    }

    /**
     * Issues a {@code POST} and parses the response.
     *
     * @param <T> the response model type
     * @param path the path below the base URI, starting with a slash
     * @param body the request model, serialized to JSON
     * @param type the response model class
     * @return the parsed response
     */
    public <T> T post(String path, Object body, Class<T> type) {
        return ParqetJson.read(send("POST", path, List.of(), ParqetJson.write(body), false), type);
    }

    private String send(String method, String path, List<Map.Entry<String, String>> query, String body, boolean idempotent) {
        var uri = URI.create(baseUri + path + encodeQuery(query));
        var response = sendWithRetries(method, uri, body, idempotent);

        if (response.statusCode() == 401 && tokens.refresh()) {
            LOG.debug("{} {} was rejected; retrying once with a refreshed token", method, path);
            response = sendWithRetries(method, uri, body, idempotent);
        }
        if (response.statusCode() / 100 == 2) {
            return response.body();
        }
        throw toException(response);
    }

    private HttpResponse<String> sendWithRetries(String method, URI uri, String body, boolean idempotent) {
        HttpResponse<String> response = null;
        for (var attempt = 1; attempt <= retry.maxAttempts(); attempt++) {
            response = sendOnce(method, uri, body);
            if (response.statusCode() / 100 == 2 || !retry.isRetryable(response.statusCode(), idempotent)) {
                return response;
            }
            if (attempt == retry.maxAttempts()) {
                break;
            }
            var failedAttempt = attempt;
            var wait = retryAfter(response).orElseGet(() -> retry.backoffAfter(failedAttempt));
            LOG.debug("{} {} returned HTTP {}; retrying in {} (attempt {}/{})", method, uri.getPath(), response.statusCode(), wait, attempt,
                    retry.maxAttempts());
            sleep(wait);
        }
        return response;
    }

    private HttpResponse<String> sendOnce(String method, URI uri, String body) {
        var builder = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .timeout(requestTimeout);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        // The Authorization header is deliberately absent from every log statement in this class.
        LOG.debug("{} {}", method, uri.getPath());
        try {
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            LOG.debug("{} {} -> HTTP {}", method, uri.getPath(), response.statusCode());
            return response;
        } catch (IOException e) {
            throw new ParqetTransportException(method + " " + uri + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ParqetTransportException(method + " " + uri + " was interrupted", e);
        }
    }

    private ParqetApiException toException(HttpResponse<String> response) {
        var error = ParqetJson.readErrorLeniently(response.body());
        return ParqetApiException.of(
                response.statusCode(),
                error.message(),
                error.error(),
                response.headers().firstValue("cf-ray").orElse(null),
                retryAfter(response).orElse(null));
    }

    /**
     * Parses {@code Retry-After}, which RFC 9110 allows to be either a number of seconds or an HTTP date.
     *
     * @param response the response to read the header from
     * @return the requested wait, empty when the header is absent or unparseable
     */
    static Optional<Duration> retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("retry-after").map(String::trim).map(HttpTransport::parseRetryAfter);
    }

    private static Duration parseRetryAfter(String value) {
        try {
            return Duration.ofSeconds(Long.parseLong(value));
        } catch (NumberFormatException notSeconds) {
            try {
                var until = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                var wait = Duration.between(Instant.now(), until);
                return wait.isNegative() ? Duration.ZERO : wait;
            } catch (DateTimeParseException notADate) {
                // Neither form; fall back to the computed backoff rather than guessing.
                return null;
            }
        }
    }

    private static void sleep(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ParqetTransportException("Interrupted while backing off before a retry", e);
        }
    }

    /**
     * Percent-encodes a value for use as a single path segment.
     * <p>
     * {@code URLEncoder} is not usable here: it encodes a space as {@code +}, which a path reads literally.
     *
     * @param value the segment to encode
     * @return the encoded segment
     */
    public static String pathSegment(String value) {
        var encoded = new StringBuilder(value.length());
        for (var b : value.getBytes(StandardCharsets.UTF_8)) {
            var c = (char) (b & 0xFF);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-' || c == '.' || c == '_' || c == '~') {
                encoded.append(c);
            } else {
                encoded.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return encoded.toString();
    }

    private static String encodeQuery(List<Map.Entry<String, String>> query) {
        if (query.isEmpty()) {
            return "";
        }
        return "?"
                + query.stream()
                        .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
    }

    /** Closes the underlying HTTP client, unless the caller supplied it. */
    @Override
    public void close() {
        if (ownsClient) {
            http.close();
        }
    }
}
