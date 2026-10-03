package io.github.exepex.commerce.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The case lookups that run on every refund and every order page find their rows through an index. */
class CaseIndexesMigrationTest {

    private static final String ORDER = "'6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c'";
    private static final String CASE = "'8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0001'";

    @Test
    void anOrdersCasesAndACasesUnsentNotesAreFoundThroughTheirIndexes() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var dataSource = new SingleConnectionDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword(), true);
            Flyway.configure().dataSource(dataSource).schemas("governance").defaultSchema("governance").load().migrate();
            var jdbc = JdbcClient.create(dataSource);
            // An empty table is cheapest to scan; the planner shows which index it can use once scanning is ruled out.
            jdbc.sql("set enable_seqscan = off").update();

            assertThat(planOf(jdbc, "select * from governance.support_case where order_id = " + ORDER
                    + " order by created_at")).contains("support_case_order");
            assertThat(planOf(jdbc, "select * from governance.support_case where order_id = " + ORDER
                    + " and status in ('PENDING', 'WITH_AGENT', 'WITH_TEAM')")).contains("support_case_order");
            assertThat(planOf(jdbc, "select * from governance.case_note where case_id = " + CASE
                    + " and sent_at is null order by created_at")).contains("case_note_case_unsent");
            dataSource.destroy();
        }
    }

    private static String planOf(JdbcClient jdbc, String query) {
        return String.join("\n", jdbc.sql("explain " + query).query(String.class).list());
    }
}
