package io.github.exepex.commerce.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentServiceApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(AgentServiceApplication.class, arguments);
    }
}
