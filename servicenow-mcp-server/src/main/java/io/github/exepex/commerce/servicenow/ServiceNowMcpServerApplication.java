package io.github.exepex.commerce.servicenow;

import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.servicenow.constants.ConfigKeys;
import io.github.exepex.commerce.servicenow.governance.GovernanceApi;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.service.registry.ImportHttpServices;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@ImportHttpServices(group = ConfigKeys.GOVERNANCE_CLIENT, types = GovernanceApi.class)
public class ServiceNowMcpServerApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(ServiceNowMcpServerApplication.class, arguments);
    }

    @Bean
    AgentDefinitions agentDefinitions() {
        return AgentDefinitions.load();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
