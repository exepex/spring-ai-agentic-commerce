package io.github.exepex.commerce.simulator;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ServiceNowSimulatorApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(ServiceNowSimulatorApplication.class, arguments);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
