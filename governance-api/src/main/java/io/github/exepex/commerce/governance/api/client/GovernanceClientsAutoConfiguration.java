package io.github.exepex.commerce.governance.api.client;

import io.github.exepex.commerce.governance.api.GovernancePaths;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * The governance clients, for a service that calls the commerce MCP server: one that sets
 * {@code spring.http.serviceclient.governance.base-url}. The commerce MCP server itself only uses the contract.
 */
@AutoConfiguration
@ConditionalOnProperty("spring.http.serviceclient." + GovernancePaths.CLIENT_GROUP + ".base-url")
@ImportHttpServices(group = GovernancePaths.CLIENT_GROUP,
        types = {AgentGovernanceClient.class, AgentSwitchesClient.class})
public class GovernanceClientsAutoConfiguration {}
