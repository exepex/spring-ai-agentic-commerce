package io.github.exepex.commerce.mcp.governance;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Serializes work on one thing, such as one order's refunds, across concurrent requests: the lock is held until the
 * caller's transaction ends, so a second request waits and then sees what the first one saved.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdvisoryLocks {

    /**
     * Waits for and takes the lock on {@code key} within the caller's transaction. Each kind of work has its own
     * {@code namespace} (0 an order's refunds, 2 an order's case of one type, 3 a notification key), so the same key
     * locked for different work does not wait on itself.
     */
    public static void lock(JdbcClient jdbc, String key, int namespace) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, " + namespace + "))")
                .param("key", key)
                .query((row, number) -> number)
                .single();
    }
}
