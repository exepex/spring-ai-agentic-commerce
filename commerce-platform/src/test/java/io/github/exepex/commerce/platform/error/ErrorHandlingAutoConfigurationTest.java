package io.github.exepex.commerce.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

class ErrorHandlingAutoConfigurationTest {

    private final WebApplicationContextRunner context = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ErrorHandlingAutoConfiguration.class, WebMvcAutoConfiguration.class));

    @Test
    void withProblemDetailsOnTheServiceFailuresAreAnsweredByTheOneSharedHandler() {
        context.withPropertyValues("spring.mvc.problemdetails.enabled=true")
                .run(started -> assertThat(started).getBeans(ResponseEntityExceptionHandler.class)
                        .hasSize(1)
                        .allSatisfy((name, handler) -> assertThat(handler).isInstanceOf(ProblemDetailsExceptionHandler.class)));
    }

    @Test
    void withProblemDetailsOffTheServiceKeepsSpringBootsOwnErrorAnswers() {
        context.run(started -> assertThat(started).doesNotHaveBean(ResponseEntityExceptionHandler.class));
    }
}
