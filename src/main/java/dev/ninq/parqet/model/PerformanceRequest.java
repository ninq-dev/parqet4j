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

import dev.ninq.parqet.internal.Validate;
import java.util.List;

/**
 * What to compute performance over, as sent to {@code POST /performance}.
 * <p>
 * Several portfolios can be evaluated as one, which is how a combined view across accounts is produced. Only {@code portfolioIds} is
 * required; the API defaults the interval to the portfolio's whole history and the currency to {@code EUR}.
 *
 * <pre>{@code
 * var request = PerformanceRequest.of(portfolioId)
 *         .interval(Timeframe.of(RelativeInterval.YEAR_TO_DATE))
 *         .currency(Currency.EUR);
 * }</pre>
 *
 * @param portfolioIds the portfolios to evaluate together; must not be empty
 * @param interval the window to evaluate, or {@code null} for the API default of the whole history
 * @param currency the reporting currency, or {@code null} for the API default of {@code EUR}
 * @param filter which sub-accounts to include, or {@code null} for all of them
 */
public record PerformanceRequest(List<String> portfolioIds, Timeframe interval, Currency currency, Filter filter) {

    /**
     * Canonical constructor.
     *
     * @throws IllegalArgumentException if {@code portfolioIds} is empty
     */
    public PerformanceRequest {
        portfolioIds = Validate.requireNonEmpty(portfolioIds, "portfolioIds");
    }

    /**
     * Starts a request for one or more portfolios, with every other setting left at the API default.
     *
     * @param portfolioIds the portfolios to evaluate together
     * @return the request
     * @throws IllegalArgumentException if no portfolio is given
     */
    public static PerformanceRequest of(String... portfolioIds) {
        return new PerformanceRequest(List.of(portfolioIds), null, null, null);
    }

    /**
     * Returns a copy of this request evaluating a different window.
     *
     * @param interval the window to evaluate
     * @return the adjusted request
     */
    public PerformanceRequest interval(Timeframe interval) {
        return new PerformanceRequest(portfolioIds, interval, currency, filter);
    }

    /**
     * Returns a copy of this request reporting in a different currency.
     *
     * @param currency the reporting currency
     * @return the adjusted request
     */
    public PerformanceRequest currency(Currency currency) {
        return new PerformanceRequest(portfolioIds, interval, currency, filter);
    }

    /**
     * Returns a copy of this request restricted to the given sub-accounts.
     *
     * @param subAccountIds the sub-accounts to include, each of the form {@code portfolioId::subAccountId}
     * @return the adjusted request
     */
    public PerformanceRequest subAccounts(List<String> subAccountIds) {
        return new PerformanceRequest(portfolioIds, interval, currency, new Filter(subAccountIds));
    }

    /**
     * Which parts of the selected portfolios to include.
     *
     * @param subAccountIds the sub-accounts to include, never {@code null}
     */
    public record Filter(List<String> subAccountIds) {

        /** Canonical constructor. */
        public Filter {
            subAccountIds = Validate.copyOf(subAccountIds);
        }
    }
}
