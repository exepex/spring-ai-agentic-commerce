package io.github.exepex.commerce.agents;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Every service that runs or governs agents reads the same definitions, loaded once at startup. */
@AutoConfiguration
public class AgentDefinitionsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AgentDefinitions agentDefinitions() {
        return AgentDefinitions.load();
    }
}
