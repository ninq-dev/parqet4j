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
import dev.ninq.parqet.internal.Validate;
import java.util.List;
import java.util.Objects;

/**
 * Prices to push for one user-managed holding, as sent to {@code POST /portfolios/{portfolioId}/quotes/user-managed}.
 * <p>
 * Address the holding either by the id Parqet assigned it or by the {@code externalId} the integration set when creating it — the latter
 * means a sync does not have to remember Parqet's ids.
 *
 * @param identifier which holding to price
 * @param quotes the prices to store; must not be empty
 */
public record QuoteUpdate(Target identifier, List<Quote> quotes) {

    /**
     * Canonical constructor.
     *
     * @throws NullPointerException if {@code identifier} is {@code null}
     * @throws IllegalArgumentException if {@code quotes} is empty
     */
    public QuoteUpdate {
        Objects.requireNonNull(identifier, "identifier must not be null");
        quotes = Validate.requireNonEmpty(quotes, "quotes");
    }

    /**
     * Prices a holding by the id Parqet assigned it.
     *
     * @param holdingId the Parqet holding id
     * @param quotes the prices to store
     * @return the update to send
     */
    public static QuoteUpdate forHolding(String holdingId, List<Quote> quotes) {
        return new QuoteUpdate(new Target.HoldingId(holdingId), quotes);
    }

    /**
     * Prices a holding by the {@code externalId} the integration gave it.
     *
     * @param externalId the identifier set when the holding was created
     * @param quotes the prices to store
     * @return the update to send
     */
    public static QuoteUpdate forExternalId(String externalId, List<Quote> quotes) {
        return new QuoteUpdate(new Target.ExternalId(externalId), quotes);
    }

    /** Which holding a {@link QuoteUpdate} applies to. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Target.HoldingId.class, name = "holdingId"),
            @JsonSubTypes.Type(value = Target.ExternalId.class, name = "externalId"),
    })
    public sealed interface Target {

        /**
         * Returns the identifier value.
         *
         * @return the holding id or external id
         */
        String value();

        /**
         * A holding addressed by the id Parqet assigned it.
         *
         * @param value the Parqet holding id
         */
        record HoldingId(String value) implements Target {

            /**
             * Canonical constructor.
             *
             * @throws NullPointerException if {@code value} is {@code null}
             */
            public HoldingId {
                Validate.requireText(value, "holdingId");
            }
        }

        /**
         * A holding addressed by the {@code externalId} the integration set.
         *
         * @param value the external id
         */
        record ExternalId(String value) implements Target {

            /**
             * Canonical constructor.
             *
             * @throws IllegalArgumentException if {@code value} does not match {@code ^[A-Za-z0-9\-_]+$} or is longer than 255 characters
             */
            public ExternalId {
                Validate.checkExternalId(Validate.requireText(value, "externalId"));
            }
        }
    }
}
