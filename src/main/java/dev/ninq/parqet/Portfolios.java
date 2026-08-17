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
package dev.ninq.parqet;

import dev.ninq.parqet.internal.Envelopes;
import dev.ninq.parqet.internal.HttpTransport;
import dev.ninq.parqet.internal.Validate;
import dev.ninq.parqet.model.Portfolio;
import java.util.List;

/** The portfolio collection: what the user shared, and how to add to it. */
public final class Portfolios {

    private final HttpTransport transport;

    Portfolios(HttpTransport transport) {
        this.transport = transport;
    }

    /**
     * Lists the portfolios the user shared with this integration.
     * <p>
     * This is not every portfolio the user has — consent is granted per portfolio, and the API only ever shows the granted ones.
     *
     * @return the accessible portfolios, in the order the API returned them
     */
    public List<Portfolio> list() {
        return transport.get("/portfolios", List.of(), Envelopes.PortfolioList.class).items();
    }

    /**
     * Creates a portfolio and returns its id.
     * <p>
     * Requires the {@code portfolio:write} scope.
     *
     * @param name the display name, 1 to 80 characters
     * @return the id of the new portfolio
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or longer than 80 characters
     */
    public String create(String name) {
        var body = new Envelopes.PortfolioCreation(Validate.requireName(name));
        return transport.post("/portfolios", body, Envelopes.CreatedId.class).id();
    }
}
