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

/**
 * Thrown on HTTP 404: the addressed portfolio or holding does not exist, or is not covered by the portfolios the user shared with this
 * integration.
 */
public final class ParqetNotFoundException extends ParqetApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a not-found failure.
     *
     * @param statusCode 404
     * @param apiMessage the {@code message} field of the error body, may be {@code null}
     * @param apiError the {@code error} field of the error body, may be {@code null}
     * @param requestId the {@code cf-ray} response header, may be {@code null}
     */
    public ParqetNotFoundException(int statusCode, String apiMessage, String apiError, String requestId) {
        super(statusCode, apiMessage, apiError, requestId);
    }
}
