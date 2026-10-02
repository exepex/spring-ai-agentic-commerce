package io.github.exepex.commerce.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * What the MCP server's integration tests share: the real server with Postgres and Kafka in containers, one
 * WireMock server standing in for the four commerce services, an MCP client per agent, and helpers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
abstract class McpServerTestSupport {

    protected static final String ASSISTANT_TOKEN = "dev-shopping-assistant-token";
    protected static final String EXCEPTIONS_AGENT_TOKEN = "dev-order-exceptions-agent-token";

    protected static final WireMockServer SERVICES = startWireMock();

    @LocalServerPort
    protected int port;


    protected McpSyncClient assistant;
    protected McpSyncClient exceptionsAgent;

    @DynamicPropertySource
    static void pointAtWireMock(DynamicPropertyRegistry registry) {
        for (String service : List.of("catalog", "order", "payment", "shipping")) {
            registry.add("spring.http.serviceclient." + service + ".base-url", SERVICES::baseUrl);
        }
    }

    @BeforeEach
    void connectTheAgents() {
        SERVICES.resetAll();
        assistant = connect(ASSISTANT_TOKEN);
        exceptionsAgent = connect(EXCEPTIONS_AGENT_TOKEN);
    }

    @AfterEach
    void disconnect() {
        assistant.closeGracefully();
        exceptionsAgent.closeGracefully();
    }

    /** Sends both requests at the same moment and returns their HTTP statuses. */
    @SafeVarargs
    protected static List<Integer> runTogether(Callable<Integer>... requests) throws Exception {
        try (ExecutorService threads = Executors.newFixedThreadPool(requests.length)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (Callable<Integer> request : requests) {
                results.add(threads.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            return statuses;
        }
    }

    protected McpSyncClient connect(String token) {
        McpSyncClient client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
                        .build())
                .requestTimeout(Duration.ofSeconds(20))
                .build();
        client.initialize();
        return client;
    }

    protected static McpSchema.CallToolResult call(McpSyncClient client, String tool, Map<String, Object> arguments) {
        return client.callTool(new McpSchema.CallToolRequest(tool, arguments));
    }

    protected static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    protected RestClient rest() {
        return RestClient.create("http://localhost:" + port);
    }

    protected static UUID stubOrder(String customerEmail, String total) {
        UUID orderId = UUID.randomUUID();
        SERVICES.stubFor(get("/api/orders/" + orderId).willReturn(okJson("""
                {"id": "%s", "customerEmail": "%s", "status": "CONFIRMED", "total": %s, "currency": "EUR",
                 "createdAt": "2026-10-02T10:00:00Z", "lines": []}""".formatted(orderId, customerEmail, total))));
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(okJson("""
                {"id": "%s", "orderId": "%s", "amount": %s, "refundedAmount": 0, "refundable": %s, "currency": "EUR",
                 "status": "SUCCEEDED", "refunds": []}""".formatted(UUID.randomUUID(), orderId, total, total))));
        return orderId;
    }

    protected static void stubRefundSucceeds(UUID orderId) {
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id": "%s", "amount": 39.50, "reason": "item out of stock", "providerReference": "re_test"}"""
                        .formatted(UUID.randomUUID()))));
    }

    /** One server for every test class, which share one Spring context; it stops when the test JVM does. */
    protected static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        return server;
    }
}
