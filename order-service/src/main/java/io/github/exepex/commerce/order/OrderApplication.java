package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.constants.ConfigKeys;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.service.registry.ImportHttpServices;

@SpringBootApplication
@EnableScheduling
@ImportHttpServices(group = ConfigKeys.CATALOG_CLIENT_GROUP, types = CatalogHttpApi.class)
@ImportHttpServices(group = ConfigKeys.PAYMENT_CLIENT_GROUP, types = PaymentHttpApi.class)
public class OrderApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(OrderApplication.class, arguments);
    }
}
