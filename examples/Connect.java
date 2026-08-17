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

import dev.ninq.parqet.ParqetClient;
import dev.ninq.parqet.auth.TokenProvider;

/** Shared helper: build a client from {@code PARQET_ACCESS_TOKEN}. */
final class Connect {

    private Connect() {
    }

    static ParqetClient open() {
        var token = System.getenv("PARQET_ACCESS_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("PARQET_ACCESS_TOKEN is not set. Run examples/run.sh Authorize <client-id> first.");
            System.exit(2);
        }
        return ParqetClient.builder().tokens(TokenProvider.of(token)).userAgent("parqet4j-examples").build();
    }
}
