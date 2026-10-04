package io.github.exepex.commerce.platform.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SchedulerLockAutoConfigurationTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SchedulerLockAutoConfiguration.class))
            .withPropertyValues("commerce.scheduling.lock-table=orders.shedlock",
                    "commerce.scheduling.lock-at-most-for=30m");

    /** A job that runs longer than the lock's limit must not let a second instance start it meanwhile. */
    @Test
    void aJobsLockIsRenewedWhileTheJobRuns() {
        contexts.withBean(DataSource.class, () -> mock(DataSource.class))
                .run(context -> assertThat(context.getBean(LockProvider.class))
                        .isInstanceOf(KeepAliveLockProvider.class));
    }

    @Test
    void aServiceWithoutADatabaseHasNoLocks() {
        contexts.run(context -> assertThat(context).doesNotHaveBean(LockProvider.class));
    }
}
