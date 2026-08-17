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
package dev.ninq.parqet.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.ninq.parqet.error.ParqetProtocolException;
import dev.ninq.parqet.model.Broker;
import dev.ninq.parqet.model.Currency;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The single JSON codec used for every request and response.
 * <p>
 * The mapper is configured for a client that must tolerate a server which grows: unknown properties are ignored, and {@code null} values
 * are omitted on the way out so optional request fields stay absent rather than being sent as {@code null}.
 * <p>
 * Time is handled here rather than through {@code jackson-datatype-jsr310} — the API pins one format for each of its two time types, so two
 * small codecs cover it and keep the dependency list at two artifacts.
 */
public final class ParqetJson {

    private static final ObjectMapper MAPPER = createMapper();

    private ParqetJson() {
    }

    private static ObjectMapper createMapper() {
        var module = new SimpleModule("parqet4j");
        module.addSerializer(Instant.class, new InstantSerializer());
        module.addDeserializer(Instant.class, new InstantDeserializer());
        module.addSerializer(LocalDate.class, new LocalDateSerializer());
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer());
        // Currency and Broker tolerate values this client does not know on the way in; the
        // serializers below make sure such a value can never be sent back out unnoticed.
        module.addSerializer(Currency.class, new OpenEnumSerializer<>(Currency::isKnown, Currency::code));
        module.addSerializer(Broker.class, new OpenEnumSerializer<>(Broker::isKnown, Broker::id));

        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS))
                .addModule(module)
                .build();
    }

    /**
     * Returns the shared mapper. Exposed for tests and for callers inside this module that need a tree view; never mutate it.
     *
     * @return the configured mapper
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Parses a response body into a model.
     *
     * @param <T> the model type
     * @param json the response body
     * @param type the model class
     * @return the parsed model
     * @throws ParqetProtocolException if the body is not valid JSON or does not fit {@code type}
     */
    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonMappingException e) {
            throw new ParqetProtocolException("Response did not match the expected " + type.getSimpleName() + " shape", e);
        } catch (IOException e) {
            throw new ParqetProtocolException("Response was not valid JSON", e);
        }
    }

    /**
     * Renders a request body.
     *
     * @param value the model to serialize
     * @return the JSON body
     * @throws ParqetProtocolException if the model cannot be serialized
     */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new ParqetProtocolException("Could not serialize " + value.getClass().getSimpleName() + " to JSON", e);
        }
    }

    /**
     * Parses an error body without ever throwing: a gateway or proxy may answer with HTML, and a failure to read the explanation must not
     * replace the HTTP status the caller actually needs.
     *
     * @param body the response body, may be {@code null}
     * @return the parsed error, with {@code null} fields when nothing could be read
     */
    public static ApiError readErrorLeniently(String body) {
        if (body == null || body.isBlank()) {
            return new ApiError(null, null);
        }
        try {
            return MAPPER.readValue(body, ApiError.class);
        } catch (IOException e) {
            return new ApiError(body.length() > 200 ? body.substring(0, 200) + "…" : body, null);
        }
    }

    /**
     * The error body the API returns on a non-2xx status.
     *
     * @param message the human-readable explanation, may be {@code null}
     * @param error the short error label, may be {@code null}
     */
    public record ApiError(String message, String error) {
    }

    /**
     * Writes an enum that tolerates unknown values on the way in, refusing to write the placeholder back out. Sending {@code UNKNOWN} would
     * silently corrupt the user's data, so it fails loudly.
     *
     * @param <E> the enum type
     */
    private static final class OpenEnumSerializer<E extends Enum<E>> extends JsonSerializer<E> {

        private final Predicate<E> known;
        private final Function<E, String> wireValue;

        OpenEnumSerializer(Predicate<E> known, Function<E, String> wireValue) {
            this.known = known;
            this.wireValue = wireValue;
        }

        @Override
        public void serialize(E value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (!known.test(value)) {
                throw new ParqetProtocolException(
                        value.getDeclaringClass().getSimpleName() + "." + value.name() + " came from the API and cannot be sent back; "
                                + "this client is older than the deployed API");
            }
            gen.writeString(wireValue.apply(value));
        }
    }

    private static final class InstantSerializer extends JsonSerializer<Instant> {

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            // Instant.toString() is ISO-8601 with a literal Z and optional fraction, exactly the
            // shape the API's date-time pattern requires.
            gen.writeString(value.toString());
        }
    }

    private static final class InstantDeserializer extends JsonDeserializer<Instant> {

        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            var text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e) {
                throw new ParqetProtocolException("Expected an ISO-8601 UTC timestamp, got: " + text, e);
            }
        }
    }

    private static final class LocalDateSerializer extends JsonSerializer<LocalDate> {

        @Override
        public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.toString());
        }
    }

    private static final class LocalDateDeserializer extends JsonDeserializer<LocalDate> {

        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            var text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException e) {
                throw new ParqetProtocolException("Expected an ISO-8601 date, got: " + text, e);
            }
        }
    }
}
