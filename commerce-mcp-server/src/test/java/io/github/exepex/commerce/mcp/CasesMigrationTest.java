package io.github.exepex.commerce.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A demo database that still has work in the escalation queue keeps it when cases replace the queue: each unresolved
 * escalation becomes a pending hand-off case for people, one per order.
 */
class CasesMigrationTest {

    @Test
    void unresolvedEscalationsBecomePendingHandOffCasesOnePerOrder() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword());
            migrate(dataSource, "4");
            JdbcClient jdbc = JdbcClient.create(dataSource);
            UUID order = UUID.randomUUID();
            UUID oldest = escalation(jdbc, order, "OPEN", null, "2026-10-01T09:00:00Z", "Refund failed twice.");
            escalation(jdbc, order, "ASSIGNED", "ana@trailhead.example", "2026-10-01T10:00:00Z", "Again.");
            escalation(jdbc, UUID.randomUUID(), "RESOLVED", "ben@trailhead.example", "2026-10-01T11:00:00Z", "Done.");
            UUID withoutOrder = escalation(jdbc, null, "ASSIGNED", "ben@trailhead.example", "2026-10-01T12:00:00Z",
                    "Slack is down.");

            migrate(dataSource, "latest");

            List<Map<String, Object>> cases = jdbc.sql("select * from governance.support_case order by created_at")
                    .query().listOfRows();
            assertThat(cases).extracting(row -> row.get("id")).containsExactly(oldest, withoutOrder);
            assertThat(cases).allSatisfy(row -> {
                assertThat(row.get("type")).isEqualTo("HANDOFF");
                assertThat(row.get("status")).isEqualTo("PENDING");
                assertThat(row.get("for_people")).as("a person already had the work").isEqualTo(true);
            });
            assertThat(cases.get(0).get("title")).isEqualTo("[HANDOFF] Order " + order.toString().substring(0, 8)
                    + " needs a person");
            assertThat(cases.get(0).get("description")).isEqualTo("Refund failed twice.");
            assertThat(cases.get(1).get("title")).isEqualTo("[HANDOFF] A request needs a person");
            assertThat((String) cases.get(1).get("description"))
                    .isEqualTo("Slack is down. (It was assigned to ben@trailhead.example in the escalation queue.)");
        }
    }

    private static void migrate(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).schemas("governance").defaultSchema("governance").target(target)
                .load().migrate();
    }

    private static UUID escalation(JdbcClient jdbc, UUID orderId, String status, String assignedTo, String createdAt,
            String summary) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into governance.escalation (id, order_id, raised_by, summary, status, assigned_to, created_at)
                        values (:id, :orderId, 'order-exceptions-agent', :summary, :status, :assignedTo,
                                cast(:createdAt as timestamptz))""")
                .param("id", id)
                .param("orderId", orderId)
                .param("summary", summary)
                .param("status", status)
                .param("assignedTo", assignedTo)
                .param("createdAt", createdAt)
                .update();
        return id;
    }
}
