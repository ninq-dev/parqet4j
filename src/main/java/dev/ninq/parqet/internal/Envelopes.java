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

import dev.ninq.parqet.model.Holding;
import dev.ninq.parqet.model.NewActivity;
import dev.ninq.parqet.model.Portfolio;
import java.util.List;

/**
 * The one-field wrappers the API puts around collections and ids.
 * <p>
 * They exist only so Jackson has a concrete type to bind to; the client unwraps them before returning anything to a caller.
 */
public final class Envelopes {

    private Envelopes() {
    }

    /**
     * The body of {@code GET /portfolios}.
     *
     * @param items the portfolios shared with this integration
     */
    public record PortfolioList(List<Portfolio> items) {

        /** Canonical constructor. */
        public PortfolioList {
            items = Validate.copyOf(items);
        }
    }

    /**
     * The body of {@code GET /portfolios/{portfolioId}/holdings}.
     *
     * @param items the holdings in the portfolio
     */
    public record HoldingList(List<Holding> items) {

        /** Canonical constructor. */
        public HoldingList {
            items = Validate.copyOf(items);
        }
    }

    /**
     * The body of the endpoints that create a portfolio or a holding.
     *
     * @param id the id of the created resource
     */
    public record CreatedId(String id) {
    }

    /**
     * The body of the endpoints that return nothing meaningful.
     */
    public record Empty() {
    }

    /**
     * The body of {@code POST /portfolios/{portfolioId}/activities}.
     * <p>
     * The element type is spelled out rather than left as a wildcard: Jackson only writes the {@code assetIdentifierType} discriminator when
     * the declared element type is the annotated {@link NewActivity} base.
     *
     * @param activities the activities to book
     */
    public record ActivityBatch(List<? extends NewActivity> activities) {
    }

    /**
     * The body of {@code POST /portfolios}.
     *
     * @param name the name of the portfolio to create
     */
    public record PortfolioCreation(String name) {
    }
}
