package io.github.exepex.commerce.mcpserver.guard;

/** Whether an agent is switched on, as the server that keeps the switches says. */
public interface KillSwitch {

    /** Fails closed: a switch that cannot be read counts as off. */
    boolean isSwitchedOn(String agentId);

    /** The tool that hands work to people, which a switched-off agent may still call. */
    boolean worksWhileSwitchedOff(String tool);
}
