package io.github.exepex.commerce.platform.events;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The outbox table. Statements name the configured table, which {@link OutboxProperties} allows only as a plain
 * schema.table, so nothing but that name is ever added to them.
 */
class OutboxStore {

    private final JdbcClient jdbc;
    private final String table;
    private final String insert;
    private final String nextBatch;
    private final String delete;

    OutboxStore(JdbcClient jdbc, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.table = properties.table();
        this.insert = "insert into " + table + " (topic, event_key, payload, trace_headers) "
                + "values (:topic, :key, :payload, :traceHeaders)";
        this.nextBatch = "select id, topic, event_key, payload, trace_headers from " + table
                + " order by id limit :limit for update";
        this.delete = "delete from " + table + " where id in (:ids)";
    }

    /** Adds an event in the caller's transaction, so it is kept exactly when the change it announces is. */
    void add(String topic, String key, String payload, String traceHeaders) {
        jdbc.sql(insert)
                .param("topic", topic)
                .param("key", key)
                .param("payload", payload)
                .param("traceHeaders", traceHeaders)
                .update();
    }

    /**
     * Makes the caller's transaction the only relay of this outbox until it ends, so with several instances of a
     * service the events still leave in the order they were written. False when another relay has it.
     */
    boolean claimRelay() {
        return Boolean.TRUE.equals(jdbc.sql("select pg_try_advisory_xact_lock(hashtext(:table))")
                .param("table", table)
                .query(Boolean.class)
                .single());
    }

    /** The oldest events, in the order they were written. */
    List<OutboxRow> nextBatch(int limit) {
        return jdbc.sql(nextBatch)
                .param("limit", limit)
                .query((row, number) -> new OutboxRow(row.getLong("id"), row.getString("topic"),
                        row.getString("event_key"), row.getString("payload"), row.getString("trace_headers")))
                .list();
    }

    void remove(List<Long> ids) {
        if (!ids.isEmpty()) {
            jdbc.sql(delete).param("ids", ids).update();
        }
    }
}
