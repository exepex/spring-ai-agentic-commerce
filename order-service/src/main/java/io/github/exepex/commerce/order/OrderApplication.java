package io.github.exepex.commerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.service.registry.ImportHttpServices;

@SpringBootApplication
@EnableScheduling
@ImportHttpServices(group = "catalog", types = CatalogHttpApi.class)
@ImportHttpServices(group = "payment", types = PaymentHttpApi.class)
public class OrderApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(OrderApplication.class, arguments);
    }
}
