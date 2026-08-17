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

/**
 * 02-list-portfolios — who the token belongs to, and what it can see.
 *
 * <pre>{@code examples/run.sh ListPortfolios}</pre>
 */
public final class ListPortfolios {

    public static void main(String[] args) {
        try (var parqet = Connect.open()) {
            var me = parqet.user();
            System.out.printf("user %s, installation %s, state %s%n", me.userId(), me.installationId(), me.state().id());
            System.out.println("granted portfolios: " + me.grantedPortfolioIds());
            System.out.println();

            for (var portfolio : parqet.portfolios().list()) {
                System.out.printf("%s - %s  [%s]  created %s%s%n",
                        portfolio.id(),
                        portfolio.name(),
                        portfolio.currency(),
                        portfolio.createdAt(),
                        me.canWrite(portfolio.id()) ? "  (writable)" : "");

                for (var holding : parqet.portfolio(portfolio.id()).holdings()) {
                    System.out.printf("    %-12s %-24s %d activities%n",
                            holding.assetType().id(), describe(holding.asset()), holding.activityCount());
                }
            }
        }
    }

    /** The sealed hierarchy makes this switch exhaustive without a default branch. */
    private static String describe(HoldingAsset asset) {
        return switch (asset) {
            case HoldingAsset.Security s -> s.name() + " (" + s.isin() + ")";
            case HoldingAsset.Crypto c -> c.name() + " (" + c.symbol() + ")";
            case HoldingAsset.Commodity c -> c.name() + " " + c.amount() + c.unit().id();
            case HoldingAsset.Cash _ -> "cash account";
            case HoldingAsset.Custom _ -> "custom asset";
            case HoldingAsset.Insurance _ -> "insurance";
            case HoldingAsset.P2p _ -> "P2P";
            case HoldingAsset.RealEstate _ -> "real estate";
        };
    }
}
