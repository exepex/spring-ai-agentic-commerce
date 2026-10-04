package io.github.exepex.commerce.platform.scheduling;

import java.util.concurrent.Executors;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lets a scheduled job marked {@code @SchedulerLock} run on one instance of a service at a time, through a
 * {@code shedlock} table in the service's own schema. The database's clock decides, so instances whose clocks drift
 * still agree. The lock is renewed while the job runs, however long it takes, so no other instance starts the job
 * meanwhile. An instance that dies stops renewing it: the lock then ends within
 * {@code commerce.scheduling.lock-at-most-for}, and the job does not stay blocked for good.
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass({LockProvider.class, JdbcTemplateLockProvider.class})
@ConditionalOnBean(DataSource.class)
@EnableSchedulerLock(defaultLockAtMostFor = "${commerce.scheduling.lock-at-most-for}")
public class SchedulerLockAutoConfiguration {

    private static final String RENEWAL_THREAD = "scheduler-lock-renewal";

    @Bean
    @ConditionalOnMissingBean
    LockProvider lockProvider(DataSource dataSource, @Value("${commerce.scheduling.lock-table}") String table) {
        var locks = new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName(table)
                .usingDbTime()
                .build());
        return new KeepAliveLockProvider(locks, Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name(RENEWAL_THREAD).daemon().factory()));
    }
}
