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
package dev.ninq.parqet.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.Objects;

/** The window a performance calculation covers — either a named look-back or explicit bounds. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Timeframe.Relative.class, name = "relative"),
        @JsonSubTypes.Type(value = Timeframe.Absolute.class, name = "absolute"),
})
public sealed interface Timeframe {

    /**
     * Returns a named look-back window.
     *
     * @param value the window
     * @return the timeframe
     */
    static Timeframe of(RelativeInterval value) {
        return new Relative(value);
    }

    /**
     * Returns a window with an explicit start, running to now.
     *
     * @param start when the window opens
     * @return the timeframe
     */
    static Timeframe from(Instant start) {
        return new Absolute(start, null);
    }

    /**
     * Returns a window with explicit bounds.
     *
     * @param start when the window opens
     * @param end when the window closes
     * @return the timeframe
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    static Timeframe between(Instant start, Instant end) {
        return new Absolute(start, Objects.requireNonNull(end, "end must not be null"));
    }

    /**
     * A named look-back window.
     *
     * @param value which window
     */
    record Relative(RelativeInterval value) implements Timeframe {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Relative {
            Objects.requireNonNull(value, "value must not be null");
        }
    }

    /**
     * A window with explicit bounds.
     *
     * @param start when the window opens
     * @param end when the window closes, or {@code null} for "up to now"
     */
    record Absolute(Instant start, Instant end) implements Timeframe {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if {@code start} is {@code null}
         * @throws IllegalArgumentException if {@code end} is before {@code start}
         */
        public Absolute {
            Objects.requireNonNull(start, "start must not be null");
            if (end != null && end.isBefore(start)) {
                throw new IllegalArgumentException("end (" + end + ") must not be before start (" + start + ")");
            }
        }
    }
}
