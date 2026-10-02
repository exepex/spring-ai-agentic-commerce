package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agents.AgentDefinitions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.service.registry.ImportHttpServices;

@SpringBootApplication
@ConfigurationPropertiesScan
@ImportHttpServices(group = "governance", types = GovernanceApi.class)
public class AgentServiceApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(AgentServiceApplication.class, arguments);
    }

    @Bean
    AgentDefinitions agentDefinitions() {
        return AgentDefinitions.load();
    }
}
