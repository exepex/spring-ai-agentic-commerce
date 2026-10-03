package io.github.exepex.commerce.servicenow.incidents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import io.github.exepex.commerce.servicenow.dto.Team;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** Reading several cases' incidents at once, in as few Table API requests as ServiceNow allows. */
class ServiceNowClientTest {

    private static final String INSTANCE = "https://dev.example.com";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ServiceNowClient serviceNow = new ServiceNowClient(new ServiceNowProperties(INSTANCE, "agent.user",
            "secret", "incident-agent", "Online Shop Agent", Duration.ofMinutes(30), "Solved",
            "payments", Map.of("payments", new Team("Payments", "refunds"))), builder);

    @Test
    void aHundredAndOneCasesAreReadInTwoRequestsAndOtherLinksAreLeftOut() {
        var sysIds = IntStream.range(0, 101).mapToObj(index -> "sys-" + index).toList();
        expectQuery("sys_idIN" + String.join(",", sysIds.subList(0, 100)), sysIds.subList(0, 100));
        expectQuery("sys_idIN" + sysIds.get(100), sysIds.subList(100, 101));
        var links = new ArrayList<String>(sysIds.stream().map(ServiceNowClientTest::linkTo).toList());
        links.add("http://localhost:1/incident.do?sys_id=sys-0");
        links.add(linkTo("sys-1^ORDERBYnumber"));

        var found = serviceNow.findAllLinked(links);

        server.verify();
        assertThat(found).hasSize(101);
        assertThat(found.get(linkTo("sys-100")).number()).isEqualTo("INC-sys-100");
        assertThat(found).doesNotContainKeys("http://localhost:1/incident.do?sys_id=sys-0", linkTo("sys-1^ORDERBYnumber"));
    }

    @Test
    void anIncidentServiceNowNoLongerHasIsLeftOut() {
        expectQuery("sys_idINsys-1,sys-2", List.of("sys-1"));

        var found = serviceNow.findAllLinked(List.of(linkTo("sys-1"), linkTo("sys-2")));

        assertThat(found).containsOnlyKeys(linkTo("sys-1"));
    }

    @Test
    void noCaseNeedsNoRequest() {
        assertThat(serviceNow.findAllLinked(List.of())).isEmpty();
        server.verify();
    }

    private static String linkTo(String sysId) {
        return INSTANCE + "/incident.do?sys_id=" + sysId;
    }

    private void expectQuery(String query, List<String> answered) {
        var rows = answered.stream()
                .map(sysId -> "{\"sys_id\": {\"value\": \"%s\"}, \"number\": {\"value\": \"INC-%s\"}}".formatted(sysId, sysId))
                .collect(Collectors.joining(",", "{\"result\": [", "]}"));
        server.expect(method(HttpMethod.GET))
                .andExpect(queryIs(query))
                .andRespond(withSuccess(rows, MediaType.APPLICATION_JSON));
    }

    private static RequestMatcher queryIs(String expected) {
        return request -> {
            var encoded = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("sysparm_query");
            assertThat(URLDecoder.decode(encoded, StandardCharsets.UTF_8)).isEqualTo(expected);
        };
    }
}
