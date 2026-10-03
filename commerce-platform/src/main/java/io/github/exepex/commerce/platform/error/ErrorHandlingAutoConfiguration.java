package io.github.exepex.commerce.platform.error;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Answers every service's failures the same way, wherever a service switches problem details on. */
@AutoConfiguration(before = WebMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty("spring.mvc.problemdetails.enabled")
public class ErrorHandlingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
    ProblemDetailsExceptionHandler problemDetailsExceptionHandler() {
        return new ProblemDetailsExceptionHandler();
    }
}
