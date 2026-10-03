package io.github.exepex.commerce.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogValuesTest {

    @Test
    void aLineBreakCannotStartAForgedLogEntry() {
        assertThat(LogValues.safe("ann@example.com\nINFO Refund approved\r\tfor everyone"))
                .isEqualTo("ann@example.com_INFO Refund approved__for everyone");
    }

    @Test
    void anOrdinaryValueIsLoggedAsItIs() {
        assertThat(LogValues.safe("ann@example.com")).isEqualTo("ann@example.com");
        assertThat(LogValues.safe(null)).isNull();
    }
}
