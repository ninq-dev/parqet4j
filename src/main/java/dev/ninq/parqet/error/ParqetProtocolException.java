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
 * Thrown when a response arrived but could not be turned into a model: malformed JSON, a missing required property, or an OAuth token
 * response that does not carry an access token.
 * <p>
 * This signals a mismatch between the deployed API and this client version — unknown <em>extra</em> properties are tolerated silently and
 * never cause it.
 */
public final class ParqetProtocolException extends ParqetException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a protocol failure.
     *
     * @param message the detail message
     */
    public ParqetProtocolException(String message) {
        super(message);
    }

    /**
     * Creates a protocol failure with a cause.
     *
     * @param message the detail message
     * @param cause the underlying parse failure, may be {@code null}
     */
    public ParqetProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
