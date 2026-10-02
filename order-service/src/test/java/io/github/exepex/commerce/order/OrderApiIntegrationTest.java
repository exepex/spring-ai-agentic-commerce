package io.github.exepex.commerce.order;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Runs the real service against Postgres, with WireMock standing in for the catalog over real HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderApiIntegrationTest {

    private static final UUID SHOE = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0001");
    private static final UUID HEADLAMP = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0004");
    private static final UUID UNKNOWN_PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final WireMockServer CATALOG = startCatalog();

    @Autowired
    private MockMvcTester mockMvc;

    @DynamicPropertySource
    static void pointAtTheCatalog(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.catalog.base-url", CATALOG::baseUrl);
    }

    @BeforeEach
    void stubTheCatalog() {
        CATALOG.resetAll();
        stubProduct(SHOE, "RUN-SHOE-BLUE-42", "Trail running shoe, blue, EU 42", "129.90");
        stubProduct(HEADLAMP, "HEADLAMP-400", "Headlamp, 400 lumen", "39.50");
        CATALOG.stubFor(get("/api/products/" + UNKNOWN_PRODUCT).willReturn(problem(HttpStatus.NOT_FOUND, "no such product")));
        CATALOG.stubFor(post(urlMatching("/api/products/.+/reservations")).willReturn(aResponse().withStatus(201)));
        CATALOG.stubFor(delete(urlMatching("/api/orders/.+/reservations")).willReturn(aResponse().withStatus(204)));
    }

    @AfterAll
    static void stopTheCatalog() {
        CATALOG.stop();
    }

    @Test
    void placesAnOrderReservingEveryLineAndPricingItFromTheCatalog() {
        MvcTestResult placed = placeOrder("ada@example.com", SHOE, 2, HEADLAMP, 1);

        assertThat(placed).hasStatus(HttpStatus.CREATED);
        assertThat(placed).bodyJson().extractingPath("$.status").isEqualTo("PLACED");
        assertThat(placed).bodyJson().extractingPath("$.total").isEqualTo(299.30);
        String orderId = orderIdOf(placed);
        CATALOG.verify(postRequestedFor(urlEqualTo("/api/products/" + SHOE + "/reservations"))
                .withRequestBody(equalToJson("""
                        {"orderId": "%s", "quantity": 2}""".formatted(orderId))));
        assertThat(mockMvc.get().uri("/api/orders/{orderId}", orderId))
                .bodyJson().extractingPath("$.lines.length()").isEqualTo(2);
    }

    @Test
    void rejectsTheOrderAndReleasesWhatWasReservedWhenStockRunsShort() {
        CATALOG.stubFor(post("/api/products/" + HEADLAMP + "/reservations")
                .willReturn(problem(HttpStatus.CONFLICT, "Requested 1 of HEADLAMP-400 but only 0 available")));

        MvcTestResult rejected = placeOrder("grace@example.com", SHOE, 1, HEADLAMP, 1);

        assertThat(rejected).hasStatus(HttpStatus.CONFLICT);
        assertThat(rejected).bodyJson().extractingPath("$.detail")
                .isEqualTo("Requested 1 of HEADLAMP-400 but only 0 available");
        CATALOG.verify(deleteRequestedFor(urlMatching("/api/orders/.+/reservations")));
        assertThat(mockMvc.get().uri("/api/orders?customerEmail=grace@example.com"))
                .bodyJson().extractingPath("$.length()").isEqualTo(0);
    }

    @Test
    void rejectsAnUnknownProduct() {
        assertThat(placeOrder("linus@example.com", UNKNOWN_PRODUCT, 1)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void rejectsTheSameProductOnTwoLinesWithoutCallingTheCatalog() {
        assertThat(placeOrder("barbara@example.com", SHOE, 1, SHOE, 2)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(CATALOG.getAllServeEvents()).isEmpty();
    }

    @Test
    void reportsTheCatalogAsUnavailableWhenItCannotBeReached() {
        CATALOG.stubFor(get("/api/products/" + SHOE).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(placeOrder("edsger@example.com", SHOE, 1)).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void cancellingReleasesTheStockOnceAndKeepsTheFirstReason() {
        String orderId = orderIdOf(placeOrder("margaret@example.com", SHOE, 1));

        MvcTestResult cancelled = cancel(orderId, "customer changed their mind");
        assertThat(cancelled).hasStatusOk();
        assertThat(cancelled).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");

        assertThat(cancel(orderId, "a second reason"))
                .bodyJson().extractingPath("$.cancellationReason").isEqualTo("customer changed their mind");
        CATALOG.verify(1, deleteRequestedFor(urlEqualTo("/api/orders/" + orderId + "/reservations")));
    }

    @Test
    void rejectsAnInvalidEmail() {
        assertThat(placeOrder("not-an-email", SHOE, 1)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private static WireMockServer startCatalog() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    private static void stubProduct(UUID productId, String sku, String name, String price) {
        CATALOG.stubFor(get("/api/products/" + productId).willReturn(okJson("""
                {"id": "%s", "sku": "%s", "name": "%s", "price": %s, "currency": "EUR", "available": 10}"""
                .formatted(productId, sku, name, price))));
    }

    private static ResponseDefinitionBuilder problem(HttpStatus status, String detail) {
        return aResponse().withStatus(status.value())
                .withHeader("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .withBody("""
                        {"status": %d, "detail": "%s"}""".formatted(status.value(), detail));
    }

    private MvcTestResult placeOrder(String customerEmail, Object... productsAndQuantities) {
        StringBuilder lines = new StringBuilder();
        for (int index = 0; index < productsAndQuantities.length; index += 2) {
            if (index > 0) {
                lines.append(", ");
            }
            lines.append("""
                    {"productId": "%s", "quantity": %d}""".formatted(productsAndQuantities[index],
                    productsAndQuantities[index + 1]));
        }
        return mockMvc.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerEmail": "%s", "lines": [%s]}""".formatted(customerEmail, lines))
                .exchange();
    }

    private MvcTestResult cancel(String orderId, String reason) {
        return mockMvc.post().uri("/api/orders/{orderId}/cancellation", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "%s"}""".formatted(reason))
                .exchange();
    }

    private static String orderIdOf(MvcTestResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
