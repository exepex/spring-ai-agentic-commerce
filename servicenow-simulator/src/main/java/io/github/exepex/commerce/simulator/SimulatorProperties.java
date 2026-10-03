package io.github.exepex.commerce.simulator;

import io.github.exepex.commerce.simulator.constants.ConfigKeys;
import io.github.exepex.commerce.simulator.dto.Group;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The instance the simulator stands in for, under {@code simulator}.
 *
 * @param username the integration user, the only login the simulator accepts
 * @param password its password
 * @param groups the assignment groups
 */
@ConfigurationProperties(ConfigKeys.SIMULATOR_PREFIX)
public record SimulatorProperties(String username, String password, List<Group> groups) {

    public SimulatorProperties {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
}
