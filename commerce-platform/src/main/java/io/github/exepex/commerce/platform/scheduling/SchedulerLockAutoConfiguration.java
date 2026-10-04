package io.github.exepex.commerce.platform.scheduling;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
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
 * still agree. A lock is held at most {@code commerce.scheduling.lock-at-most-for}, so an instance that dies while
 * running a job does not block it for good.
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass({LockProvider.class, JdbcTemplateLockProvider.class})
@ConditionalOnBean(DataSource.class)
@EnableSchedulerLock(defaultLockAtMostFor = "${commerce.scheduling.lock-at-most-for}")
public class SchedulerLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    LockProvider lockProvider(DataSource dataSource, @Value("${commerce.scheduling.lock-table}") String table) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName(table)
                .usingDbTime()
                .build());
    }
}
