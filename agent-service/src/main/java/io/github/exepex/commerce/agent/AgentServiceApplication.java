package io.github.exepex.commerce.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.service.registry.ImportHttpServices;

@SpringBootApplication
@ConfigurationPropertiesScan
@ImportHttpServices(group = "governance", types = GovernanceApi.class)
public class AgentServiceApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(AgentServiceApplication.class, arguments);
    }
}
