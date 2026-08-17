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
package dev.ninq.parqet;

import dev.ninq.parqet.auth.TokenProvider;
import dev.ninq.parqet.internal.HttpTransport;
import dev.ninq.parqet.internal.Validate;
import dev.ninq.parqet.model.ConnectInfo;
import dev.ninq.parqet.model.PerformanceRequest;
import dev.ninq.parqet.model.PortfolioPerformance;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The entry point to the Parqet Connect API.
 * <p>
 * A client is immutable, thread-safe, and meant to be created once and shared. Close it when the application shuts down — that releases the
 * HTTP client, unless one was supplied by the caller.
 *
 * <pre>{@code
 * try (var parqet = ParqetClient.builder().tokens(TokenProvider.of(accessToken)).build()) {
 *     var me = parqet.user();
 *     for (var portfolio : parqet.portfolios().list()) {
 *         var holdings = parqet.portfolio(portfolio.id()).holdings();
 *         System.out.println(portfolio.name() + ": " + holdings.size() + " holdings");
 *     }
 * }
 * }</pre>
 * <p>
 * Every call throws {@link dev.ninq.parqet.error.ParqetException} or one of its subtypes on failure; nothing returns {@code null} to signal
 * an error.
 */
public final class ParqetClient implements AutoCloseable {

    /** The production API root, {@code https://connect.parqet.com}. */
    public static final URI DEFAULT_BASE_URI = URI.create("https://connect.parqet.com");

    private final HttpTransport transport;
    private final Portfolios portfolios;

    private ParqetClient(Builder builder) {
        var tokens = Objects.requireNonNull(builder.tokens, "tokens must be set");
        var ownsClient = builder.http == null;
        var http = ownsClient
                ? HttpClient.newBuilder().connectTimeout(builder.connectTimeout).build()
                : builder.http;
        this.transport = new HttpTransport(
                builder.baseUri, http, ownsClient, builder.requestTimeout, tokens, builder.retryPolicy, builder.userAgent);
        this.portfolios = new Portfolios(transport);
    }

    /**
     * Starts building a client.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fetches who the token belongs to and which portfolios it may touch.
     * <p>
     * This is the one call that keeps working after a user deletes their Connect account — it then reports
     * {@link dev.ninq.parqet.model.ConnectState#DELETED} while everything else answers {@code 410 Gone}.
     *
     * @return the connection info
     */
    public ConnectInfo user() {
        return transport.get("/user", List.of(), ConnectInfo.class);
    }

    /**
     * Returns the portfolio collection — listing and creating portfolios.
     *
     * @return the portfolios resource
     */
    public Portfolios portfolios() {
        return portfolios;
    }

    /**
     * Returns a handle on one portfolio. Creating it performs no I/O and does not check that the portfolio exists.
     *
     * @param portfolioId the portfolio id
     * @return a handle for the portfolio's holdings, activities and quotes
     * @throws NullPointerException if {@code portfolioId} is {@code null}
     * @throws IllegalArgumentException if {@code portfolioId} is blank
     */
    public PortfolioResource portfolio(String portfolioId) {
        return new PortfolioResource(transport, Validate.requireText(portfolioId, "portfolioId"));
    }

    /**
     * Computes performance across one or more portfolios.
     *
     * @param request what to evaluate, over which window, in which currency
     * @return the aggregate figures, per-holding breakdown, allocation and time series
     * @throws NullPointerException if {@code request} is {@code null}
     */
    public PortfolioPerformance performance(PerformanceRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return transport.post("/performance", request, PortfolioPerformance.class);
    }

    /** Releases the HTTP client, unless the caller supplied one. */
    @Override
    public void close() {
        transport.close();
    }

    /** Builder for {@link ParqetClient}. */
    public static final class Builder {

        private URI baseUri = DEFAULT_BASE_URI;
        private TokenProvider tokens;
        private HttpClient http;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(30);
        private RetryPolicy retryPolicy = RetryPolicy.defaults();
        private String userAgent = "parqet4j";

        private Builder() {
        }

        /**
         * Sets where the bearer token for each call comes from. Required.
         *
         * @param tokens a static token, or a provider that refreshes one
         * @return this builder
         */
        public Builder tokens(TokenProvider tokens) {
            this.tokens = Objects.requireNonNull(tokens, "tokens must not be null");
            return this;
        }

        /**
         * Overrides the API root. Useful for pointing tests at a stub server.
         *
         * @param baseUri the API root; a trailing slash is stripped
         * @return this builder
         */
        public Builder baseUri(URI baseUri) {
            Objects.requireNonNull(baseUri, "baseUri must not be null");
            var text = baseUri.toString();
            this.baseUri = text.endsWith("/") ? URI.create(text.substring(0, text.length() - 1)) : baseUri;
            return this;
        }

        /**
         * Supplies the HTTP client. The caller keeps ownership: {@link ParqetClient#close()} will not close it.
         *
         * @param http the HTTP client
         * @return this builder
         */
        public Builder httpClient(HttpClient http) {
            this.http = Objects.requireNonNull(http, "http must not be null");
            return this;
        }

        /**
         * Sets the connection timeout. Ignored when an HTTP client is supplied.
         *
         * @param connectTimeout the timeout; must be positive
         * @return this builder
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
            return this;
        }

        /**
         * Sets how long one request may take before it is abandoned.
         *
         * @param requestTimeout the timeout; must be positive
         * @return this builder
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
            return this;
        }

        /**
         * Sets when a failed request is re-issued. Defaults to {@link RetryPolicy#defaults()}.
         *
         * @param retryPolicy the policy, or {@link RetryPolicy#none()} to disable retrying
         * @return this builder
         */
        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
            return this;
        }

        /**
         * Sets the {@code User-Agent} sent with every request. Naming your integration here helps Parqet support trace it.
         *
         * @param userAgent the user agent
         * @return this builder
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = Validate.requireText(userAgent, "userAgent");
            return this;
        }

        /**
         * Builds the client.
         *
         * @return a new {@code ParqetClient}
         * @throws NullPointerException if no token provider was set
         */
        public ParqetClient build() {
            return new ParqetClient(this);
        }

        private static Duration requirePositive(Duration value, String field) {
            Objects.requireNonNull(value, field + " must not be null");
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException(field + " must be positive, was " + value);
            }
            return value;
        }
    }
}
