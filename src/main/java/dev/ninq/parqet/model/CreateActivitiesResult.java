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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ninq.parqet.internal.Validate;
import java.util.List;

/**
 * The outcome of a batch write to {@code POST /portfolios/{portfolioId}/activities}.
 * <p>
 * The API accepts a batch partially: activities it could book appear in {@link #createdIds()}, and the rest come back individually
 * explained. A caller that sends a batch must therefore check {@link #rejected()} rather than relying on the HTTP status alone.
 *
 * @param createdIds the ids of the activities that were booked
 * @param rejected the activities that were not booked, each pointing back at its position in the request
 */
public record CreateActivitiesResult(List<String> createdIds, List<Rejection> rejected) {

    /** Canonical constructor. */
    public CreateActivitiesResult {
        createdIds = Validate.copyOf(createdIds);
        rejected = Validate.copyOf(rejected);
    }

    /**
     * Returns whether every activity in the batch was booked.
     *
     * @return {@code true} if nothing was rejected
     */
    public boolean isCompletelyAccepted() {
        return rejected.isEmpty();
    }

    @JsonCreator
    static CreateActivitiesResult fromJson(
            @JsonProperty("createdActivities") List<CreatedWire> created,
            @JsonProperty("notCreatedActivities") List<RejectedWire> notCreated) {
        return new CreateActivitiesResult(
                created == null ? List.of() : created.stream().map(CreatedWire::id).toList(),
                notCreated == null ? List.of() : notCreated.stream().map(RejectedWire::error).filter(r -> r != null).toList());
    }

    /**
     * Why one activity of a batch was not booked.
     *
     * @param originalIndex the index of the activity in the submitted list
     * @param code the machine-readable error code
     * @param message the human-readable explanation
     */
    public record Rejection(int originalIndex, String code, String message) {
    }

    record CreatedWire(@JsonProperty("_id") String id) {
    }

    record RejectedWire(Rejection error) {
    }
}
