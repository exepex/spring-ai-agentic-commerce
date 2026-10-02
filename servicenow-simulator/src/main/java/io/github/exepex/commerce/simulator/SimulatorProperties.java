package io.github.exepex.commerce.simulator;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The instance the simulator stands in for, under {@code simulator}.
 *
 * @param username the integration user, the only login the simulator accepts
 * @param password its password
 * @param groups the assignment groups
 */
@ConfigurationProperties("simulator")
public record SimulatorProperties(String username, String password, List<Group> groups) {

    /**
     * @param name the group's name
     * @param people the user names of its members
     */
    public record Group(String name, List<String> people) {

        public Group {
            people = people == null ? List.of() : List.copyOf(people);
        }
    }

    public SimulatorProperties {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
}
