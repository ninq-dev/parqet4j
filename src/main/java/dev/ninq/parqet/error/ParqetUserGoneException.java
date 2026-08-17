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
 * Thrown on HTTP 410: the user deleted their Parqet Connect account.
 * <p>
 * Every call other than {@code GET /user} fails this way once the account is gone; {@code /user} keeps reporting {@code state = deleted}
 * until the data is purged 30 days later. Integrations should treat this as terminal — drop the stored tokens and stop syncing.
 */
public final class ParqetUserGoneException extends ParqetApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a user-gone failure.
     *
     * @param statusCode 410
     * @param apiMessage the {@code message} field of the error body, may be {@code null}
     * @param apiError the {@code error} field of the error body, may be {@code null}
     * @param requestId the {@code cf-ray} response header, may be {@code null}
     */
    public ParqetUserGoneException(int statusCode, String apiMessage, String apiError, String requestId) {
        super(statusCode, apiMessage, apiError, requestId);
    }
}
