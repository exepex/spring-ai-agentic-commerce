package io.github.exepex.commerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.service.registry.ImportHttpServices;

@SpringBootApplication
@ImportHttpServices(group = "catalog", types = CatalogHttpApi.class)
public class OrderApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(OrderApplication.class, arguments);
    }
}
