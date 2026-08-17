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
package dev.ninq.parqet.error;

import java.time.Duration;
import java.util.Optional;

/**
 * Thrown on HTTP 429 after the client exhausted its retries.
 * <p>
 * {@link #retryAfter()} carries the parsed {@code Retry-After} header when the API sent one; the default retry policy already waits for it
 * before re-issuing the request.
 */
public final class ParqetRateLimitException extends ParqetApiException {

    private static final long serialVersionUID = 1L;

    /** The parsed {@code Retry-After} header, or {@code null}. */
    private final Duration retryAfter;

    /**
     * Creates a rate-limit failure.
     *
     * @param statusCode 429
     * @param apiMessage the {@code message} field of the error body, may be {@code null}
     * @param apiError the {@code error} field of the error body, may be {@code null}
     * @param requestId the {@code cf-ray} response header, may be {@code null}
     * @param retryAfter the parsed {@code Retry-After} header, may be {@code null}
     */
    public ParqetRateLimitException(
            int statusCode, String apiMessage, String apiError, String requestId, Duration retryAfter) {
        super(statusCode, apiMessage, apiError, requestId);
        this.retryAfter = retryAfter;
    }

    /**
     * Returns how long the API asked the caller to wait before retrying.
     *
     * @return the retry delay, empty when the response carried no {@code Retry-After} header
     */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
