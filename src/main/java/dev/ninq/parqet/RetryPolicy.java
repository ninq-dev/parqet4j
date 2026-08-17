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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * When and how long to wait before re-issuing a failed request.
 * <p>
 * Reads are retried on {@code 429} and on any {@code 5xx}. Writes are retried on {@code 429} only: a rate-limited request never reached the
 * booking logic, while a {@code 5xx} may well have, and a duplicated activity is worse than a surfaced error.
 * <p>
 * Backoff grows geometrically and is then fully jittered — the actual wait is drawn uniformly from zero up to the computed bound, which
 * keeps a fleet of clients from retrying in lockstep. A {@code Retry-After} header always wins over the computed value.
 *
 * @param maxAttempts how many times a request may be sent in total; 1 disables retrying
 * @param initialBackoff the bound on the wait after the first failure
 * @param maxBackoff the ceiling the bound grows to
 * @param multiplier the factor the bound grows by after each attempt
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff, double multiplier) {

    /**
     * Canonical constructor.
     *
     * @throws IllegalArgumentException if any value is out of range
     */
    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, was " + maxAttempts);
        }
        Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
        Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
        if (initialBackoff.isNegative() || maxBackoff.isNegative()) {
            throw new IllegalArgumentException("backoff durations must not be negative");
        }
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff (" + maxBackoff + ") must not be shorter than initialBackoff (" + initialBackoff
                    + ")");
        }
        if (multiplier < 1) {
            throw new IllegalArgumentException("multiplier must be at least 1, was " + multiplier);
        }
    }

    /**
     * Returns the default policy: three attempts, backing off from 250 ms towards 5 s.
     *
     * @return the default policy
     */
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofMillis(250), Duration.ofSeconds(5), 2.0);
    }

    /**
     * Returns a policy that never retries, surfacing the first failure.
     *
     * @return a single-attempt policy
     */
    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, Duration.ZERO, 1.0);
    }

    /**
     * Returns whether a status code is worth retrying.
     *
     * @param statusCode the HTTP status the API returned
     * @param idempotent whether re-sending the request is safe — {@code true} for reads
     * @return {@code true} if another attempt should be made
     */
    public boolean isRetryable(int statusCode, boolean idempotent) {
        return statusCode == 429 || (idempotent && statusCode >= 500);
    }

    /**
     * Returns how long to wait before attempt number {@code attempt}.
     *
     * @param attempt the 1-based number of the attempt that just failed
     * @return a jittered wait, never longer than {@link #maxBackoff()}
     */
    public Duration backoffAfter(int attempt) {
        var bound = initialBackoff.toMillis() * Math.pow(multiplier, Math.max(0, attempt - 1));
        var capped = (long) Math.min(bound, maxBackoff.toMillis());
        return capped <= 0 ? Duration.ZERO : Duration.ofMillis(ThreadLocalRandom.current().nextLong(capped + 1));
    }
}
