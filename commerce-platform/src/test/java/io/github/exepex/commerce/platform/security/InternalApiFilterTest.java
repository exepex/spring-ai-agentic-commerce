package io.github.exepex.commerce.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalApiFilterTest {

    private final InternalApiFilter filter = new InternalApiFilter("service-token",
            List.of("POST /api/payments", "POST /api/payments/{orderId}/refunds"));

    @Test
    void aRefundWithoutTheServiceTokenIsRefused() throws Exception {
        var response = call("POST", "/api/payments/42/refunds", null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getErrorMessage()).isEqualTo(InternalApiFilter.TOKEN_REQUIRED);
    }

    @Test
    void aRefundWithAnotherTokenIsRefused() throws Exception {
        assertThat(call("POST", "/api/payments/42/refunds", "Bearer dev-shopping-assistant-token").getStatus())
                .isEqualTo(401);
    }

    @Test
    void aServiceWithTheTokenIsLetThrough() throws Exception {
        assertThat(call("POST", "/api/payments/42/refunds", "Bearer service-token").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/payments", "Bearer service-token").getStatus()).isEqualTo(200);
    }

    @Test
    void endpointsTheBrowserUsesAreLeftAlone() throws Exception {
        assertThat(call("GET", "/api/payments/42", null).getStatus()).isEqualTo(200);
        assertThat(call("GET", "/api/payments/42/refunds", null).getStatus()).isEqualTo(200);
        assertThat(call("PUT", "/api/admin/simulated-outage", null).getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse call(String method, String path, String authorization) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
