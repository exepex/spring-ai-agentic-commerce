package io.github.exepex.commerce.simulator.dto;

import java.util.List;

/**
 * An assignment group of the simulated instance.
 *
 * @param name the group's name
 * @param people the user names of its members
 */
public record Group(String name, List<String> people) {

    public Group {
        people = people == null ? List.of() : List.copyOf(people);
    }
}
