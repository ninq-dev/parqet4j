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
import dev.ninq.parqet.model.Activity;
import dev.ninq.parqet.model.ActivityPage;
import dev.ninq.parqet.model.CreateActivitiesResult;
import dev.ninq.parqet.model.Holding;
import dev.ninq.parqet.model.NewActivity;
import dev.ninq.parqet.model.NewHolding;
import dev.ninq.parqet.model.QuoteUpdate;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Everything that hangs off one portfolio: its holdings, its activities, and the quotes for its user-managed assets.
 * <p>
 * Obtain one with {@link ParqetClient#portfolio(String)}. The handle is stateless and safe to reuse.
 */
public final class PortfolioResource {

    private final HttpTransport transport;
    private final String portfolioId;
    private final String basePath;

    PortfolioResource(HttpTransport transport, String portfolioId) {
        this.transport = transport;
        this.portfolioId = portfolioId;
        this.basePath = "/portfolios/" + HttpTransport.pathSegment(portfolioId);
    }

    /**
     * Returns the id of the portfolio this handle addresses.
     *
     * @return the portfolio id
     */
    public String id() {
        return portfolioId;
    }

    /**
     * Lists every holding in the portfolio.
     *
     * @return the holdings, in the order the API returned them
     */
    public List<Holding> holdings() {
        return transport.get(basePath + "/holdings", List.of(), Envelopes.HoldingList.class).items();
    }

    /**
     * Fetches one page of activities.
     * <p>
     * Use {@link #activityStream(ActivityQuery)} unless you need to hold the cursor yourself — for example to resume a sync across process
     * restarts.
     *
     * @param query which activities to fetch, and from which cursor
     * @return the page, together with the cursor for the next one
     * @throws NullPointerException if {@code query} is {@code null}
     */
    public ActivityPage activities(ActivityQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return transport.get(basePath + "/activities", query.toParameters(), ActivityPage.class);
    }

    /**
     * Streams every activity matching the query, following the cursor across pages.
     * <p>
     * The stream is lazy: no request is made until it is consumed, and each further page is fetched only when the previous one runs out.
     * Because it does I/O while iterating, it must be consumed on a thread that may block, and any {@code ParqetException} surfaces from the
     * terminal operation.
     *
     * @param query which activities to fetch
     * @return a sequential stream over every matching activity
     * @throws NullPointerException if {@code query} is {@code null}
     */
    public Stream<Activity> activityStream(ActivityQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return StreamSupport.stream(new PageSpliterator(query), false);
    }

    /**
     * Books a batch of activities.
     * <p>
     * The API accepts a batch partially, so check {@link CreateActivitiesResult#rejected()} — a successful HTTP status does not mean every
     * activity landed. Requires the {@code portfolio:write} scope.
     *
     * @param activities the activities to book; must not be empty
     * @return which activities were booked, and why the others were not
     * @throws IllegalArgumentException if {@code activities} is empty
     */
    public CreateActivitiesResult createActivities(List<? extends NewActivity> activities) {
        var body = new Envelopes.ActivityBatch(Validate.requireNonEmpty(activities, "activities"));
        return transport.post(basePath + "/activities", body, CreateActivitiesResult.class);
    }

    /**
     * Books a single activity.
     *
     * @param activity the activity to book
     * @return which activities were booked, and why the others were not
     * @throws NullPointerException if {@code activity} is {@code null}
     */
    public CreateActivitiesResult createActivity(NewActivity activity) {
        return createActivities(List.of(Objects.requireNonNull(activity, "activity must not be null")));
    }

    /**
     * Creates a holding and returns its id.
     * <p>
     * Each kind of holding has its own endpoint; the variant of {@code holding} selects it. Securities and crypto holdings are not created this
     * way — booking an activity against an ISIN or ticker creates them. Requires the {@code portfolio:write} scope.
     *
     * @param holding the holding to create
     * @return the id of the new holding
     * @throws NullPointerException if {@code holding} is {@code null}
     */
    public String createHolding(NewHolding holding) {
        Objects.requireNonNull(holding, "holding must not be null");
        var path = switch (holding) {
        case NewHolding.Cash _ -> "/holdings/cash";
        case NewHolding.Commodity _ -> "/holdings/commodity";
        case NewHolding.Custom _ -> "/holdings/custom";
        case NewHolding.RealEstate _ -> "/holdings/real-estate";
        case NewHolding.Insurance _ -> "/holdings/insurance";
        case NewHolding.P2p _ -> "/holdings/p2p";
        };
        return transport.post(basePath + path, holding, Envelopes.CreatedId.class).id();
    }

    /**
     * Pushes prices for a user-managed holding.
     * <p>
     * Custom holdings have no market price, so this is how their value is kept current. Requires the {@code portfolio:write} scope.
     *
     * @param update which holding to price, and with what
     * @throws NullPointerException if {@code update} is {@code null}
     */
    public void pushQuotes(QuoteUpdate update) {
        Objects.requireNonNull(update, "update must not be null");
        transport.post(basePath + "/quotes/user-managed", update, Envelopes.Empty.class);
    }

    /** Walks the cursor, fetching one page at a time and handing out its activities. */
    private final class PageSpliterator extends Spliterators.AbstractSpliterator<Activity> {

        private final ActivityQuery query;
        private Iterator<Activity> page = Collections.emptyIterator();
        private String cursor;
        private boolean started;

        private PageSpliterator(ActivityQuery query) {
            super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
            this.query = query;
            this.cursor = query.cursor();
        }

        @Override
        public boolean tryAdvance(Consumer<? super Activity> action) {
            while (!page.hasNext()) {
                if (started && cursor == null) {
                    return false;
                }
                var fetched = activities(started ? query.withCursor(cursor) : query);
                started = true;
                cursor = fetched.cursor();
                page = fetched.activities().iterator();
                if (!page.hasNext() && cursor == null) {
                    return false;
                }
            }
            action.accept(page.next());
            return true;
        }
    }
}
