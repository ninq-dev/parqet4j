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

import dev.ninq.parqet.model.HoldingAsset;
import dev.ninq.parqet.model.HoldingPerformance;
import dev.ninq.parqet.model.PerformanceRequest;
import dev.ninq.parqet.model.RelativeInterval;
import dev.ninq.parqet.model.Timeframe;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Locale;

/**
 * 04-performance — KPIs and the per-holding breakdown, across one or more portfolios.
 *
 * <pre>{@code examples/run.sh Performance <portfolio-id> [more-portfolio-ids…] [--interval ytd]}</pre>
 */
public final class Performance {

    public static void main(String[] args) {
        var flag = Arrays.asList(args).indexOf("--interval");
        if (args.length < 1 || flag == 0 || flag == args.length - 1) {
            usage();
        }
        var interval = RelativeInterval.YEAR_TO_DATE;
        if (flag >= 0) {
            try {
                interval = RelativeInterval.fromId(args[flag + 1]);
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
                usage();
            }
        }
        var portfolioIds = Arrays.stream(args, 0, flag < 0 ? args.length : flag).toArray(String[]::new);

        try (var parqet = Connect.open()) {
            var result = parqet.performance(
                    PerformanceRequest.of(portfolioIds).interval(Timeframe.of(interval)));

            var kpis = result.performance();
            System.out.printf("%s to %s%n", result.interval().start(), result.interval().end());
            System.out.printf("  valuation      %s -> %s  (%s)%n",
                    money(kpis.valuationAtIntervalStart()), money(kpis.valuationAtIntervalEnd()), signed(kpis.valuationChange()));
            System.out.printf("  TTWROR         %s%n", percent(kpis.ttwrorIfPresent().orElse(Double.NaN)));
            System.out.printf("  XIRR           %s%n", percent(kpis.xirrIfPresent().orElse(Double.NaN)));
            System.out.printf("  unrealized     %s net%n", money(kpis.unrealizedGains().gainNet()));
            System.out.printf("  realized       %s net%n", money(kpis.realizedGains().gainNet()));
            System.out.printf("  dividends      %s net%n",
                    kpis.dividendsIfPresent().map(d -> money(d.gainNet())).orElse("—"));
            System.out.printf("  fees / taxes   %s / %s%n", money(kpis.fees()), money(kpis.taxes()));

            System.out.println("\nholdings:");
            result.holdings().stream()
                    .sorted((a, b) -> b.position().currentValue().compareTo(a.position().currentValue()))
                    .forEach(holding -> System.out.printf("  %-32s %12s  %8s%n",
                            label(holding),
                            money(holding.position().currentValue()),
                            percent(holding.performance().ttwrorIfPresent().orElse(Double.NaN))));
        }
    }

    private static void usage() {
        System.err.println("usage: Performance <portfolio-id>... [--interval 1d|1m|ytd|1y|max]");
        System.exit(2);
    }

    /** Names the holding: the user's nickname, else the asset's own name, else just its type. */
    private static String label(HoldingPerformance holding) {
        if (holding.nickname() != null) {
            return truncate(holding.nickname());
        }
        var name = switch (holding.asset()) {
            case HoldingAsset.Security security -> security.name();
            case HoldingAsset.Crypto crypto -> crypto.name();
            case HoldingAsset.Commodity commodity -> commodity.name();
            default -> holding.assetType().id();
        };
        return truncate(name);
    }

    private static String truncate(String name) {
        return name.length() <= 32 ? name : name.substring(0, 31) + "…";
    }

    /** Money comes back at full {@code BigDecimal} precision; two decimals is what a report wants. */
    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String signed(BigDecimal amount) {
        return (amount.signum() < 0 ? "" : "+") + money(amount);
    }

    /** The API already reports these KPIs in percent, so there is no factor of 100 to apply. */
    private static String percent(double value) {
        return Double.isNaN(value) ? "—" : String.format(Locale.ROOT, "%+.2f%%", value);
    }
}
