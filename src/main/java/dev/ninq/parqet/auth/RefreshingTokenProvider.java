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
package dev.ninq.parqet.auth;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TokenProvider} that renews its access token through {@link ParqetOAuth}.
 * <p>
 * Refreshes are serialised by a {@link ReentrantLock} rather than {@code synchronized} so the blocking token call does not pin a virtual
 * thread to its carrier. Concurrent callers that arrive while a refresh is in flight re-check the token after acquiring the lock and reuse
 * the result instead of triggering a second exchange.
 */
final class RefreshingTokenProvider implements TokenProvider {

    /** How far ahead of the real expiry a token is treated as stale. */
    static final long SKEW_SECONDS = 60;

    private static final Logger LOG = LoggerFactory.getLogger(RefreshingTokenProvider.class);

    private static final Duration SKEW = Duration.ofSeconds(SKEW_SECONDS);

    private final ParqetOAuth oauth;
    private final Consumer<Tokens> onRefresh;
    private final ReentrantLock lock = new ReentrantLock();

    private Tokens tokens;

    RefreshingTokenProvider(ParqetOAuth oauth, Tokens initial, Consumer<Tokens> onRefresh) {
        this.oauth = Objects.requireNonNull(oauth, "oauth must not be null");
        this.onRefresh = Objects.requireNonNull(onRefresh, "onRefresh must not be null");
        this.tokens = TokenProvider.requireRefreshable(initial);
    }

    @Override
    public String accessToken() {
        lock.lock();
        try {
            if (tokens.isExpired(SKEW)) {
                renew("expiry");
            }
            return tokens.accessToken();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean refresh() {
        lock.lock();
        try {
            var previous = tokens.accessToken();
            renew("rejected token");
            return !previous.equals(tokens.accessToken());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Tokens currentTokens() {
        lock.lock();
        try {
            return tokens;
        } finally {
            lock.unlock();
        }
    }

    private void renew(String reason) {
        LOG.debug("Refreshing Parqet access token ({})", reason);
        var renewed = oauth.refresh(tokens.refreshToken());
        // A rotating server may omit the refresh token when it stays valid; keep the old one so the
        // grant survives.
        var carried = renewed.refreshToken() != null
                ? renewed
                : new Tokens(
                        renewed.accessToken(), renewed.tokenType(), tokens.refreshToken(), renewed.expiresAt(), renewed.scope());
        onRefresh.accept(carried);
        tokens = carried;
    }
}
