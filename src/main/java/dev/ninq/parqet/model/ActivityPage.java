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
import java.util.Optional;

/**
 * One page of activities plus the cursor that fetches the next one.
 * <p>
 * Prefer {@code PortfolioResource.activityStream} unless you need to control paging yourself; it follows the cursor for you.
 *
 * @param activities the activities on this page, never {@code null}
 * @param cursor the cursor for the next page, {@code null} on the last page
 */
public record ActivityPage(List<Activity> activities, String cursor) {

    /** Canonical constructor. */
    public ActivityPage {
        activities = Validate.copyOf(activities);
    }

    /**
     * Returns whether another page follows.
     *
     * @return {@code true} while the API reported a cursor
     */
    public boolean hasMore() {
        return cursor != null;
    }

    /**
     * Returns the cursor for the next page.
     *
     * @return the cursor, empty on the last page
     */
    public Optional<String> nextCursor() {
        return Optional.ofNullable(cursor);
    }
}
