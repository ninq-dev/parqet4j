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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The result of {@code POST /performance}: aggregate figures, a per-holding breakdown, the allocation, and a time series for charting.
 *
 * @param performance the aggregate figures across every selected portfolio
 * @param holdings the per-holding breakdown, never {@code null}
 * @param interval the window the API actually evaluated
 * @param netAllocations how the value splits across holdings, separating positive and negative positions
 * @param charts the time series, never {@code null}
 */
public record PortfolioPerformance(Performance performance, List<HoldingPerformance> holdings, DateRange interval,
        NetAllocations netAllocations, List<ChartPoint> charts) {

    /** Canonical constructor. */
    public PortfolioPerformance {
        holdings = Validate.copyOf(holdings);
        charts = Validate.copyOf(charts);
    }

    /**
     * The window the calculation covered.
     *
     * @param start the first day included
     * @param end the last day included
     */
    public record DateRange(LocalDate start, LocalDate end) {
    }

    /**
     * How the portfolio's value distributes across its holdings.
     * <p>
     * Positive and negative positions are reported separately, and {@code base} is whichever side is larger in absolute terms — that is what
     * the per-holding shares are expressed against.
     *
     * @param net the sum of the positive and negative sides
     * @param base the absolute value of the larger side
     * @param positive the holdings worth more than zero
     * @param negative the holdings worth less than zero
     */
    public record NetAllocations(BigDecimal net, BigDecimal base, Side positive, Side negative) {

        /**
         * One side of the allocation.
         *
         * @param total the summed value of this side
         * @param shareOfBase this side's share of {@code base}
         * @param holdings each holding's value, keyed by holding id, never {@code null}
         */
        public record Side(BigDecimal total, double shareOfBase, Map<String, BigDecimal> holdings) {

            /** Canonical constructor. */
            public Side {
                holdings = holdings == null ? Map.of() : Map.copyOf(holdings);
            }
        }
    }

    /**
     * One sample of the portfolio's history.
     *
     * @param mark how to read {@code date} — see {@link ChartMark}
     * @param date when this sample was taken
     * @param values the figures at that point
     */
    public record ChartPoint(ChartMark mark, Instant date, Values values) {

        /**
         * The figures carried by a chart point.
         *
         * @param history the total market value at this point
         * @param capitalHistory the net capital contributed up to this point
         * @param perfHistory the total performance for the interval, in percent
         * @param perfHistoryUnrealized the unrealized performance for the interval, in percent
         * @param ttwror the cumulative true time-weighted rate of return, in percent
         * @param drawdown the percentage below the previous peak; zero at a peak, negative otherwise
         */
        public record Values(BigDecimal history, BigDecimal capitalHistory, double perfHistory, double perfHistoryUnrealized, double ttwror,
                double drawdown) {
        }
    }
}
