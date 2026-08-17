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
 * Root of every unchecked exception thrown by parqet4j.
 * <p>
 * Callers that do not care to distinguish failure modes can catch this single type. The three branches below it separate the causes:
 * <ul>
 * <li>{@link ParqetApiException} — the API answered with a non-2xx status.
 * <li>{@link ParqetTransportException} — the request never produced a response (I/O, timeout).
 * <li>{@link ParqetProtocolException} — a response arrived but could not be understood.
 * </ul>
 */
public class ParqetException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message
     */
    public ParqetException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause the underlying failure, may be {@code null}
     */
    public ParqetException(String message, Throwable cause) {
        super(message, cause);
    }
}
