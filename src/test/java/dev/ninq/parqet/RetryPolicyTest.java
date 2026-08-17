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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void retriesRateLimitsForReadsAndWritesAlike() {
        var policy = RetryPolicy.defaults();

        assertThat(policy.isRetryable(429, true)).isTrue();
        assertThat(policy.isRetryable(429, false)).isTrue();
    }

    @Test
    void retriesServerErrorsOnlyForIdempotentCalls() {
        var policy = RetryPolicy.defaults();

        assertThat(policy.isRetryable(500, true)).isTrue();
        assertThat(policy.isRetryable(503, true)).isTrue();
        // Re-sending a write that may already have been applied would duplicate activities.
        assertThat(policy.isRetryable(500, false)).isFalse();
    }

    @Test
    void doesNotRetryClientErrors() {
        var policy = RetryPolicy.defaults();

        assertThat(policy.isRetryable(400, true)).isFalse();
        assertThat(policy.isRetryable(401, true)).isFalse();
        assertThat(policy.isRetryable(404, true)).isFalse();
        assertThat(policy.isRetryable(410, true)).isFalse();
    }

    @Test
    void jittersWithinTheGeometricBoundAndNeverExceedsTheCeiling() {
        var policy = new RetryPolicy(6, Duration.ofMillis(100), Duration.ofMillis(800), 2.0);

        // Attempt 1 -> bound 100ms, attempt 2 -> 200ms, attempt 3 -> 400ms, then capped at 800ms.
        IntStream.rangeClosed(1, 50).forEach(i -> {
            assertThat(policy.backoffAfter(1)).isBetween(Duration.ZERO, Duration.ofMillis(100));
            assertThat(policy.backoffAfter(2)).isBetween(Duration.ZERO, Duration.ofMillis(200));
            assertThat(policy.backoffAfter(3)).isBetween(Duration.ZERO, Duration.ofMillis(400));
            assertThat(policy.backoffAfter(9)).isBetween(Duration.ZERO, Duration.ofMillis(800));
        });
    }

    @Test
    void neverWaitsWhenRetryingIsDisabled() {
        var policy = RetryPolicy.none();

        assertThat(policy.maxAttempts()).isEqualTo(1);
        assertThat(policy.backoffAfter(1)).isZero();
    }

    @Test
    void rejectsAnIncoherentConfiguration() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofMillis(1), Duration.ofMillis(2), 2.0))
                .hasMessageContaining("maxAttempts must be at least 1");
        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofSeconds(10), Duration.ofSeconds(1), 2.0))
                .hasMessageContaining("must not be shorter than");
        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofMillis(1), Duration.ofMillis(2), 0.5))
                .hasMessageContaining("multiplier must be at least 1");
    }
}
