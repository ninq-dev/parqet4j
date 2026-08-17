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
 * The result of {@code GET /user}: who the token belongs to and what it may touch.
 * <p>
 * This is the only call that still succeeds once {@link #state()} is {@link ConnectState#DELETED}; every other endpoint answers
 * {@code 410 Gone} from then on.
 *
 * @param userId the Parqet user the token was issued for
 * @param installationId the installation of this integration for that user
 * @param state whether the Connect account is still active
 * @param permissions the portfolios this integration may read or write, never {@code null}
 */
public record ConnectInfo(String userId, String installationId, ConnectState state, List<Permission> permissions) {

    /**
     * Canonical constructor.
     *
     * @throws NullPointerException if {@code userId}, {@code installationId}, or {@code state} is {@code null}
     */
    public ConnectInfo {
        Validate.requireText(userId, "userId");
        Validate.requireText(installationId, "installationId");
        java.util.Objects.requireNonNull(state, "state must not be null");
        permissions = Validate.copyOf(permissions);
    }

    /**
     * Returns whether the account is usable — a shorthand for {@code state() == ConnectState.ACTIVE}.
     *
     * @return {@code true} while the user's Connect account exists
     */
    public boolean isActive() {
        return state == ConnectState.ACTIVE;
    }

    /**
     * Returns whether this integration may write to the given portfolio.
     *
     * @param portfolioId the portfolio to check
     * @return {@code true} if a {@code write} permission covers that portfolio
     */
    public boolean canWrite(String portfolioId) {
        return permissions.stream()
                .anyMatch(p -> p.action() == PermissionAction.WRITE && p.resourceId().equals(portfolioId));
    }

    /**
     * Returns the ids of every portfolio the user shared with this integration.
     *
     * @return the granted portfolio ids, in the order the API listed them
     */
    public List<String> grantedPortfolioIds() {
        return permissions.stream().map(Permission::resourceId).distinct().toList();
    }

    /**
     * A single grant: one action on one portfolio.
     *
     * @param action whether the grant allows reading or writing
     * @param resourceType the kind of resource; always {@code portfolio} today
     * @param resourceId the id of the granted portfolio
     */
    public record Permission(PermissionAction action, String resourceType, String resourceId) {

        /**
         * Canonical constructor.
         *
         * @throws NullPointerException if any argument is {@code null}
         */
        public Permission {
            java.util.Objects.requireNonNull(action, "action must not be null");
            Validate.requireText(resourceType, "resourceType");
            Validate.requireText(resourceId, "resourceId");
        }
    }
}
