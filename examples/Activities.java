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

import dev.ninq.parqet.ActivityQuery;
import dev.ninq.parqet.model.Activity;
import dev.ninq.parqet.model.ActivityType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.stream.Collectors;

/**
 * 03-activities — stream every activity of a portfolio, following the cursor.
 *
 * <pre>{@code examples/run.sh Activities <portfolio-id> [buy|sell|dividend|…]}</pre>
 */
public final class Activities {

    /** The API books activities in UTC; printing them there keeps the column fixed-width and the date unambiguous. */
    private static final DateTimeFormatter BOOKED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm'Z'").withZone(ZoneOffset.UTC);

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: Activities <portfolio-id> [activity-type]");
            System.exit(2);
        }
        var query = ActivityQuery.builder().limit(ActivityQuery.MAX_LIMIT);
        if (args.length > 1) {
            query.types(ActivityType.fromId(args[1]));
        }

        try (var parqet = Connect.open()) {
            // The stream is lazy: it fetches the first page when consumption starts, and each further
            // page only when the previous one runs out. The EnumMap keeps the summary in ActivityType
            // declaration order — groupingBy's default HashMap would reshuffle it on every run.
            var byType = parqet.portfolio(args[0]).activityStream(query.build())
                    .peek(Activities::print)
                    .collect(Collectors.groupingBy(
                            Activity::type,
                            () -> new EnumMap<>(ActivityType.class),
                            Collectors.reducing(BigDecimal.ZERO, Activity::amountNet, BigDecimal::add)));

            System.out.println("\nnet by activity type:");
            byType.forEach((type, total) -> System.out.printf("  %-14s %12s%n", type.id(), money(total)));
        }
    }

    private static void print(Activity activity) {
        System.out.printf("%s  %-14s %-14s %12s x %-12s = %12s %s%s%n",
                BOOKED_AT.format(activity.datetime()),
                activity.type().id(),
                activity.holdingAssetType().id(),
                quantity(activity.shares()),
                quantity(activity.price()),
                money(activity.amountNet()),
                activity.currency(),
                activity.fxIfPresent().map(fx -> "  (from " + fx.originalCurrency() + ")").orElse(""));
    }

    /** Money comes back at full {@code BigDecimal} precision; two decimals is what a report wants. */
    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Shares and unit prices cannot be pinned to two decimals — a crypto position is fractional, and its price may be too. Rounding to ten
     * significant digits drops the artefacts of the wire's doubles without flattening a small number to zero.
     */
    private static String quantity(BigDecimal value) {
        return value.round(new MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString();
    }
}
