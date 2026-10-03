package io.github.exepex.commerce.mcpserver.guard;

import io.github.exepex.commerce.mcpserver.security.AgentRegistry;
import io.github.exepex.commerce.mcpserver.security.McpServerSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/** The governed tool call, for a server that says how it keeps switches, refuses calls and records them. */
@AutoConfiguration(after = McpServerSecurityAutoConfiguration.class)
@ConditionalOnBean({AgentRegistry.class, KillSwitch.class, ToolRefusals.class, ToolCallAudit.class})
public class GovernedToolCallsAutoConfiguration {

    @Bean
    GovernedToolCalls governedToolCalls(AgentRegistry agents, KillSwitch killSwitch, ToolRefusals refusals,
            ToolCallAudit audit) {
        return new GovernedToolCalls(agents, killSwitch, refusals, audit);
    }
}
