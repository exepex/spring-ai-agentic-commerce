package io.github.exepex.commerce.platform.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * Settings every service shares, such as where traces go, kept in {@code commerce-platform.properties}. They come last,
 * so a service's own {@code application.yml} or the environment can still change them.
 */
public class PlatformDefaults implements EnvironmentPostProcessor {

    static final String RESOURCE = "commerce-platform.properties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            environment.getPropertySources().addLast(new ResourcePropertySource(RESOURCE, new ClassPathResource(RESOURCE)));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
