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
 * Thrown when a request never produced an HTTP response: connection failure, TLS problem, read timeout, or an interrupted call. The cause
 * is the underlying {@code IOException} or {@code InterruptedException}.
 */
public final class ParqetTransportException extends ParqetException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a transport failure.
     *
     * @param message the detail message
     * @param cause the underlying I/O or interruption failure
     */
    public ParqetTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
