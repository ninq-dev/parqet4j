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
 * Thrown when the Parqet Connect API answers with a non-2xx status.
 * <p>
 * The API reports failures as a JSON body of the shape
 * <code>{"message": "Unauthorized", "statusCode": 401, "error": "Unauthorized"}</code>. Both string fields are optional in practice, so
 * they are exposed as {@link Optional}.
 * <p>
 * Use {@link #of} to obtain the most specific subtype for a status code.
 */
public class ParqetApiException extends ParqetException {

    private static final long serialVersionUID = 1L;

    /** The HTTP status code the API answered with. */
    private final int statusCode;

    /** The {@code message} field of the error body, or {@code null}. */
    private final String apiMessage;

    /** The {@code error} field of the error body, or {@code null}. */
    private final String apiError;

    /** The {@code cf-ray} response header, or {@code null}. */
    private final String requestId;

    /**
     * Creates an API exception.
     *
     * @param statusCode the HTTP status code returned by the API
     * @param apiMessage the {@code message} field of the error body, may be {@code null}
     * @param apiError the {@code error} field of the error body, may be {@code null}
     * @param requestId the {@code cf-ray} response header, may be {@code null}
     */
    public ParqetApiException(int statusCode, String apiMessage, String apiError, String requestId) {
        super(describe(statusCode, apiMessage, apiError, requestId));
        this.statusCode = statusCode;
        this.apiMessage = apiMessage;
        this.apiError = apiError;
        this.requestId = requestId;
    }

    /**
     * Returns the most specific exception type for the given status code.
     *
     * @param statusCode the HTTP status code returned by the API
     * @param apiMessage the {@code message} field of the error body, may be {@code null}
     * @param apiError the {@code error} field of the error body, may be {@code null}
     * @param requestId the {@code cf-ray} response header, may be {@code null}
     * @param retryAfter the parsed {@code Retry-After} header, may be {@code null}
     * @return a {@code ParqetApiException} or one of its subtypes
     */
    public static ParqetApiException of(
            int statusCode, String apiMessage, String apiError, String requestId, Duration retryAfter) {
        return switch (statusCode) {
        case 401, 403 -> new ParqetAuthException(statusCode, apiMessage, apiError, requestId);
        case 404 -> new ParqetNotFoundException(statusCode, apiMessage, apiError, requestId);
        case 410 -> new ParqetUserGoneException(statusCode, apiMessage, apiError, requestId);
        case 429 -> new ParqetRateLimitException(statusCode, apiMessage, apiError, requestId, retryAfter);
        default -> statusCode >= 500
                ? new ParqetServerException(statusCode, apiMessage, apiError, requestId)
                : new ParqetApiException(statusCode, apiMessage, apiError, requestId);
        };
    }

    private static String describe(int statusCode, String apiMessage, String apiError, String requestId) {
        var sb = new StringBuilder("Parqet API returned HTTP ").append(statusCode);
        if (apiMessage != null && !apiMessage.isBlank()) {
            sb.append(": ").append(apiMessage);
        } else if (apiError != null && !apiError.isBlank()) {
            sb.append(": ").append(apiError);
        }
        if (requestId != null && !requestId.isBlank()) {
            sb.append(" (request ").append(requestId).append(')');
        }
        return sb.toString();
    }

    /**
     * Returns the HTTP status code returned by the API.
     *
     * @return the HTTP status code
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Returns the {@code message} field of the API error body.
     *
     * @return the API message, empty when the body carried none
     */
    public Optional<String> apiMessage() {
        return Optional.ofNullable(apiMessage);
    }

    /**
     * Returns the {@code error} field of the API error body.
     *
     * @return the API error label, empty when the body carried none
     */
    public Optional<String> apiError() {
        return Optional.ofNullable(apiError);
    }

    /**
     * Returns the request identifier taken from the {@code cf-ray} response header, useful when reporting a problem to Parqet support.
     *
     * @return the request id, empty when the response carried none
     */
    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }
}
