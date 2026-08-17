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

import dev.ninq.parqet.model.ActivityType;
import dev.ninq.parqet.model.AssetType;
import dev.ninq.parqet.model.Broker;
import dev.ninq.parqet.model.Currency;
import dev.ninq.parqet.model.NewActivity;
import dev.ninq.parqet.model.NewHolding;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 05-sync-activities — write side: create a cash holding and book a few activities.
 *
 * <p>Needs a token with {@code portfolio:write}. It <em>does</em> modify the portfolio you point it
 * at, so use one you do not mind touching.
 *
 * <pre>{@code examples/run.sh SyncActivities <portfolio-id>}</pre>
 */
public final class SyncActivities {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: SyncActivities <portfolio-id>");
            System.exit(2);
        }

        try (var parqet = Connect.open()) {
            if (!parqet.user().canWrite(args[0])) {
                System.err.println("This token has no write permission for portfolio " + args[0] + ".");
                System.exit(1);
            }
            var portfolio = parqet.portfolio(args[0]);

            // externalId is what lets a later run recognise what this one created.
            var cashId = portfolio.createHolding(NewHolding.cash("parqet4j demo cash")
                    .currency(Currency.EUR)
                    .referenceAccountFor(AssetType.SECURITY)
                    .externalId("parqet4j-demo-cash")
                    .build());
            System.out.println("created cash holding " + cashId);

            var now = Instant.now();
            var result = portfolio.createActivities(List.of(
                    NewActivity.cash(ActivityType.DEPOSIT, cashId)
                            .amount(new BigDecimal("5000.00"))
                            .currency(Currency.EUR)
                            .datetime(now.minusSeconds(86_400))
                            .description("Opening balance")
                            .externalId("parqet4j-demo-deposit-1")
                            .build(),
                    NewActivity.security(ActivityType.BUY, "US0378331005")
                            .shares(new BigDecimal("10"))
                            .price(new BigDecimal("234.20"))
                            .currency(Currency.USD)
                            .datetime(now)
                            .fee(new BigDecimal("1.00"))
                            .broker(Broker.TRADE_REPUBLIC)
                            .externalId("parqet4j-demo-buy-1")
                            .build()));

            // A 2xx does not mean everything landed — the API accepts a batch partially.
            System.out.println("booked: " + result.createdIds());
            result.rejected().forEach(rejection -> System.out.printf(
                    "rejected #%d (%s): %s%n", rejection.originalIndex(), rejection.code(), rejection.message()));
        }
    }
}
