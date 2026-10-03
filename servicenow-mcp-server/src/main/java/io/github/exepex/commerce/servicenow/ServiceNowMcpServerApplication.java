package io.github.exepex.commerce.servicenow;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.mcpserver.security.ServerTools;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ServiceNowMcpServerApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(ServiceNowMcpServerApplication.class, arguments);
    }

    /** What an agent may call here: the {@code servicenow} tools its definition lists. */
    @Bean
    ServerTools serverTools() {
        return AgentDefinition::servicenowTools;
    }
}
