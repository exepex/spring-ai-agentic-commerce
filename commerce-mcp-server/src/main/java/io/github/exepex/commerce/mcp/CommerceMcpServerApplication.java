package io.github.exepex.commerce.mcp;

import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.mcp.downstream.CatalogApi;
import io.github.exepex.commerce.mcp.downstream.OrderApi;
import io.github.exepex.commerce.mcp.downstream.PaymentApi;
import io.github.exepex.commerce.mcp.downstream.ShippingApi;
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
@ImportHttpServices(group = "catalog", types = CatalogApi.class)
@ImportHttpServices(group = "order", types = OrderApi.class)
@ImportHttpServices(group = "payment", types = PaymentApi.class)
@ImportHttpServices(group = "shipping", types = ShippingApi.class)
public class CommerceMcpServerApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(CommerceMcpServerApplication.class, arguments);
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
