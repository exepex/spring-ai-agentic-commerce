package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Works ServiceNow incidents first: gathers the facts from the shop, fixes what it may, and resolves the incident or
 * hands it to the team whose work it is. When it is switched off, fails, or finishes without doing either, code hands
 * the incident to the default team, so every incident ends with an owner.
 */
@Service
public class IncidentAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentAgent.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** How the ServiceNow MCP server marks a call its rules refuse, which asking again will not change. */
    private static final String REFUSED = "Refused: ";
    /** The longest note the ServiceNow MCP server accepts. */
    private static final int MAX_NOTE_LENGTH = 4000;

    private final ChatClient chatClient;
    private final McpToolboxes toolboxes;
    private final AgentSwitchboard switchboard;
    private final DecisionRecorder decisions;
    private final AgentDefinition definition;
    private final String systemPrompt;

    IncidentAgent(ChatModel chatModel, McpToolboxes toolboxes, AgentSwitchboard switchboard, DecisionRecorder decisions,
            AgentDefinitions definitions, AgentProperties properties) {
        this.definition = definitions.get(AgentSwitchboard.INCIDENT_AGENT);
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(ClaudeOptions.forAgent(definition.model(), definition.effort()))
                .build();
        this.toolboxes = toolboxes;
        this.switchboard = switchboard;
        this.decisions = decisions;
        this.systemPrompt = definition.systemPrompt(properties.slack().isConfigured() ? properties.slack().channelId() : null);
    }

    /**
     * @param linkedOrderId the order the incident is about, from its Correlation ID field; empty when it names none,
     *     and then the agent may investigate and hand over but not change any order
     */
    public void handleIncident(String number, String linkedOrderId, String incidentEvent) {
        boolean enabled;
        try {
            enabled = switchboard.isEnabled(AgentSwitchboard.INCIDENT_AGENT);
        } catch (RuntimeException unreachable) {
            throw new HandOffFailedException("Could not read the kill switch, so incident " + number
                    + " was neither worked nor handed to a team", unreachable);
        }
        if (!enabled) {
            handToTeam(number, "The incident agent is switched off, so this incident goes straight to a person. "
                    + "Nothing was checked or changed yet.");
            return;
        }
        Instant started = Instant.now();
        ToolRun run = new ToolRun(null, definition.toolCallBudget(),
                linkedOrderId == null || linkedOrderId.isBlank() ? Set.of() : Set.of(linkedOrderId),
                () -> stillOwns(number));
        ChatResponse response;
        try {
            response = chatClient.prompt()
                    .system(systemPrompt)
                    .user("Work ServiceNow incident " + number + ". It was announced as:\n" + incidentEvent)
                    .toolCallbacks(toolboxes.incidentAgentTools())
                    .toolContext(Map.of(ToolRun.CONTEXT_KEY, run))
                    .call()
                    .chatResponse();
        } catch (RuntimeException failure) {
            LOGGER.error("The incident agent failed on incident {}", number, failure);
            handToTeam(number, "The incident agent failed while working this incident (" + failure.getMessage()
                    + "). Check its earlier work notes, then finish it.");
            return;
        }
        String summary = ClaudeReply.textOf(response);
        decisions.record(AgentSwitchboard.INCIDENT_AGENT, orderOf(linkedOrderId), "Incident " + number + ": " + summary,
                "Triggered by ServiceNow incident " + number + ": " + incidentEvent, response,
                Duration.between(started, Instant.now()));
        if (!isFinished(number, run)) {
            handToTeam(number, "The incident agent finished without resolving this incident or handing it to a team, "
                    + "so a person must finish it. The agent said: " + summary);
        }
    }

    /** The linked order, so the decision shows on its timeline; null when the incident names no valid order id. */
    private static UUID orderOf(String linkedOrderId) {
        try {
            return linkedOrderId == null || linkedOrderId.isBlank() ? null : UUID.fromString(linkedOrderId.strip());
        } catch (IllegalArgumentException notAnOrderId) {
            return null;
        }
    }

    /**
     * Whether the incident is still the agent's: the ServiceNow MCP server lets it read only incidents assigned to it
     * and in progress. When ServiceNow cannot be reached the answer is no, so no order changes on a guess.
     */
    private boolean stillOwns(String number) {
        try {
            return !Boolean.TRUE.equals(toolboxes.callAsIncidentAgent("get_incident", Map.of("number", number)).isError());
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Could not check that incident {} is still the agent's", number, unavailable);
            return false;
        }
    }

    /** Finished means resolved or handed to a team in this run, for this incident; the model's summary is not trusted. */
    static boolean isFinished(String number, ToolRun run) {
        return run.succeeded().stream()
                .filter(call -> "resolve_incident".equals(call.tool()) || "assign_to_team".equals(call.tool()))
                .anyMatch(call -> number.equals(JSON.readTree(call.arguments()).path("number").asString("")));
    }

    /**
     * Hands the incident to the default team. If ServiceNow or its MCP server cannot be reached, the failure is passed
     * on, so Kafka delivers the incident again. A refusal means the incident is no longer the agent's, for example
     * because a person took it: then there is nothing left to hand over.
     */
    private void handToTeam(String number, String note) {
        McpSchema.CallToolResult result;
        try {
            result = toolboxes.callAsIncidentAgent("assign_to_team", Map.of("number", number, "note", abbreviate(note)));
        } catch (RuntimeException unavailable) {
            throw new HandOffFailedException("Could not hand incident " + number + " to a team: " + note, unavailable);
        }
        if (Boolean.TRUE.equals(result.isError())) {
            String message = textOf(result);
            if (message.startsWith(REFUSED)) {
                // Usually a person took the incident meanwhile. Whatever the reason, an incident the agent still owns
                // goes to the default team once its claim is stale, so it is never left without an owner.
                LOGGER.warn("Incident {} was not handed to a team: {}", number, message);
                return;
            }
            throw new HandOffFailedException("ServiceNow did not take the hand-off of incident " + number + ": " + message,
                    null);
        }
    }

    /** A hand-off note fits ServiceNow's limit, however long the agent's summary was. */
    private static String abbreviate(String note) {
        return note.length() <= MAX_NOTE_LENGTH ? note : note.substring(0, MAX_NOTE_LENGTH - 1) + "…";
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .findFirst()
                .orElse("");
    }
}
