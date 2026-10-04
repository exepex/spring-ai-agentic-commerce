package io.github.exepex.commerce.mcp;

import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** Migrates a test database the way the server does, for tests that need it at a given version. */
final class GovernanceMigrations {

    private GovernanceMigrations() {
    }

    /**
     * Migrates up to {@code target}, a version or "latest". Flyway locks with a session-level lock, as the server does
     * (commerce-platform.properties), so a migration that builds an index concurrently does not wait for Flyway itself.
     */
    static void migrate(DataSource dataSource, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("governance")
                .defaultSchema("governance")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .target(target)
                .load()
                .migrate();
    }
}
