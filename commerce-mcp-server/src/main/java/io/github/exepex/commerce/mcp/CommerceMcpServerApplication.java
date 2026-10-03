package io.github.exepex.commerce.mcp;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.mcp.constants.ConfigKeys;
import io.github.exepex.commerce.mcp.downstream.CatalogApi;
import io.github.exepex.commerce.mcp.downstream.OrderApi;
import io.github.exepex.commerce.mcp.downstream.PaymentApi;
import io.github.exepex.commerce.mcp.downstream.ShippingApi;
import io.github.exepex.commerce.mcpserver.security.ServerTools;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.service.registry.ImportHttpServices;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@ImportHttpServices(group = ConfigKeys.CATALOG_CLIENT, types = CatalogApi.class)
@ImportHttpServices(group = ConfigKeys.ORDER_CLIENT, types = OrderApi.class)
@ImportHttpServices(group = ConfigKeys.PAYMENT_CLIENT, types = PaymentApi.class)
@ImportHttpServices(group = ConfigKeys.SHIPPING_CLIENT, types = ShippingApi.class)
public class CommerceMcpServerApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(CommerceMcpServerApplication.class, arguments);
    }

    /** An agent may call the shop's tools its definition lists under {@code commerce}. */
    @Bean
    ServerTools serverTools() {
        return AgentDefinition::commerceTools;
    }
}
