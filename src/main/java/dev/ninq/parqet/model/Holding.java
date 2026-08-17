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
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * One position in a portfolio, as returned by {@code GET /portfolios/{portfolioId}/holdings}.
 *
 * @param id the holding id, used when booking activities against this position
 * @param activityCount how many activities are booked on this holding
 * @param logo a logo image for the asset, {@code null} when Parqet has none
 * @param nickname the user's name for this holding, {@code null} when unset
 * @param asset what the holding is invested in
 * @param externalId the identifier a previous write set on this holding, {@code null} when unset
 * @param subAccount the sub-account the holding lives in, as {@code portfolioId::subAccountId}
 * @param currency the currency the holding is denominated in
 */
public record Holding(
        String id,
        int activityCount,
        URI logo,
        String nickname,
        HoldingAsset asset,
        String externalId,
        String subAccount,
        Currency currency) {

    /**
     * Canonical constructor.
     *
     * @throws NullPointerException if {@code id}, {@code asset}, {@code subAccount}, or {@code currency} is {@code null}
     * @throws IllegalArgumentException if {@code externalId} or {@code subAccount} is malformed
     */
    public Holding {
        Validate.requireText(id, "id");
        Objects.requireNonNull(asset, "asset must not be null");
        Validate.checkExternalId(externalId);
        Validate.requireSubAccountId(subAccount);
        Objects.requireNonNull(currency, "currency must not be null");
    }

    /**
     * Returns the asset type of this holding — a shorthand for {@code asset().type()}.
     *
     * @return the asset type
     */
    public AssetType assetType() {
        return asset.type();
    }

    /**
     * Returns the logo image for this holding's asset.
     *
     * @return the logo URI, empty when Parqet has none
     */
    public Optional<URI> logoUri() {
        return Optional.ofNullable(logo);
    }

    /**
     * Returns the user's name for this holding.
     *
     * @return the nickname, empty when unset
     */
    public Optional<String> nicknameIfSet() {
        return Optional.ofNullable(nickname);
    }

    /**
     * Returns the external identifier previously written for this holding.
     *
     * @return the external id, empty when unset
     */
    public Optional<String> externalIdIfSet() {
        return Optional.ofNullable(externalId);
    }
}
