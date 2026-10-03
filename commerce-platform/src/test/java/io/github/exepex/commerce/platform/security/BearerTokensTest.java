package io.github.exepex.commerce.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BearerTokensTest {

    @Test
    void aTokenTravelsAsABearerHeaderAndIsReadBack() {
        var header = BearerTokens.authorization("dev-incident-agent-token");

        assertThat(header).isEqualTo("Bearer dev-incident-agent-token");
        assertThat(BearerTokens.tokenIn(header)).contains("dev-incident-agent-token");
    }

    @Test
    void aMissingOrOtherHeaderPresentsNoToken() {
        assertThat(BearerTokens.tokenIn(null)).isEmpty();
        assertThat(BearerTokens.tokenIn("Basic dXNlcjpwYXNz")).isEmpty();
    }
}
