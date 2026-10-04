package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agent.constants.AgentIds;
import io.github.exepex.commerce.agent.constants.AuditTexts;
import io.github.exepex.commerce.agent.constants.HandOffNotes;
import io.github.exepex.commerce.agent.constants.McpValues;
import io.github.exepex.commerce.agent.constants.Prompts;
import io.github.exepex.commerce.agent.constants.Refusals;
import io.github.exepex.commerce.agent.constants.ToolNames;
import io.github.exepex.commerce.agent.constants.ToolParameters;
import io.github.exepex.commerce.agent.exception.HandOffRejectedException;
import io.github.exepex.commerce.agent.exception.HandOffUnreachableException;
import io.github.exepex.commerce.agent.exception.KillSwitchUnreadableException;
import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.platform.logging.LogValues;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Works ServiceNow incidents first: gathers the facts from the shop, fixes what it may, and resolves the incident or
 * hands it to the team whose work it is. When it is switched off, fails, or finishes without doing either, code hands
 * the incident to the default team, so every incident ends with an owner.
 */
@Slf4j
@Service
public class IncidentAgent {

    /** The longest note the ServiceNow MCP server accepts; a longer hand-off note is cut to fit. */
    private static final int MAX_NOTE_LENGTH = 4000;

    private final ChatClient chatClient;
    private final McpToolboxes toolboxes;
    private final AgentSwitchboard switchboard;
    private final DecisionRecorder decisions;
    private final AgentDefinition definition;
    private final String systemPrompt;
    private final Duration runLimit;

    IncidentAgent(ChatModel chatModel, McpToolboxes toolboxes, AgentSwitchboard switchboard, DecisionRecorder decisions,
            AgentDefinitions definitions, AgentProperties properties) {
        this.definition = definitions.get(AgentIds.INCIDENT_AGENT);
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(ClaudeOptions.forAgent(definition.model(), definition.effort()))
                .build();
        this.toolboxes = toolboxes;
        this.switchboard = switchboard;
        this.decisions = decisions;
        this.runLimit = properties.agents().incidentRunLimit();
        this.systemPrompt = definition.systemPrompt(properties.slack().isConfigured() ? properties.slack().channelId() : null);
    }

    /**
     * @param linkedOrderId the order the incident is about, from its Correlation ID field; empty when it names none,
     *     and then the agent may investigate and hand over but not change any order
     */
    public void handleIncident(String number, String linkedOrderId, String incidentEvent) {
        boolean enabled;
        try {
            enabled = switchboard.isEnabled(AgentIds.INCIDENT_AGENT);
        } catch (RuntimeException unreachable) {
            throw new KillSwitchUnreadableException(number, unreachable);
        }
        if (!enabled) {
            handToTeam(number, HandOffNotes.AGENT_SWITCHED_OFF);
            return;
        }
        var started = Instant.now();
        var run = new ToolRun(null, definition.toolCallBudget(), number,
                linkedOrderId == null || linkedOrderId.isBlank() ? Set.of() : Set.of(linkedOrderId),
                orderId -> stillLinksTo(number, orderId), started.plus(runLimit));
        ChatResponse response;
        try {
            response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(Prompts.WORK_INCIDENT.formatted(number, incidentEvent))
                    .toolCallbacks(toolboxes.incidentAgentTools())
                    .toolContext(Map.of(McpValues.TOOL_RUN_CONTEXT_KEY, run))
                    .call()
                    .chatResponse();
        } catch (RuntimeException failure) {
            log.error("The incident agent failed on incident {}", number, failure);
            handToTeam(number, HandOffNotes.AGENT_FAILED.formatted(failure.getMessage()));
            return;
        }
        var summary = ClaudeReply.textOf(response);
        decisions.record(AgentIds.INCIDENT_AGENT, orderOf(linkedOrderId),
                AuditTexts.INCIDENT_WORKED.formatted(number, summary),
                AuditTexts.TRIGGERED_BY_INCIDENT.formatted(number, incidentEvent), response,
                Duration.between(started, Instant.now()));
        if (!isFinished(number, run)) {
            handToTeam(number, HandOffNotes.NOT_FINISHED.formatted(summary));
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
     * Whether the incident is still the agent's and still linked to the order: the ServiceNow MCP server lets it read
     * only incidents assigned to it and in progress, and the service desk may have corrected the linked order since the
     * run started. When ServiceNow cannot be reached the answer is no, so no order changes on a guess.
     */
    boolean stillLinksTo(String number, String orderId) {
        try {
            var incident = toolboxes.callAsIncidentAgent(ToolNames.GET_INCIDENT,
                    Map.of(ToolParameters.NUMBER, number));
            if (Boolean.TRUE.equals(incident.isError())) {
                return false;
            }
            var linked = orderOf(ToolJson.textField(McpResults.textOf(incident), ToolParameters.LINKED_ORDER_ID));
            return linked != null && linked.equals(orderOf(orderId));
        } catch (RuntimeException unavailable) {
            log.warn("Could not check that incident {} is still the agent's and about order {}", number, orderId,
                    unavailable);
            return false;
        }
    }

    /** Finished means resolved or handed to a team in this run, for this incident; the model's summary is not trusted. */
    static boolean isFinished(String number, ToolRun run) {
        return run.succeeded().stream()
                .filter(call -> ToolNames.RESOLVE_INCIDENT.equals(call.tool())
                        || ToolNames.ASSIGN_TO_TEAM.equals(call.tool()))
                .anyMatch(call -> number.equals(ToolJson.textField(call.arguments(), ToolParameters.NUMBER)));
    }

    /**
     * Hands the incident to the default team. If ServiceNow or its MCP server cannot be reached, the failure is passed
     * on, so Kafka delivers the incident again. A refusal means the incident is no longer the agent's, for example
     * because a person took it: then there is nothing left to hand over.
     */
    private void handToTeam(String number, String note) {
        McpSchema.CallToolResult result;
        try {
            result = toolboxes.callAsIncidentAgent(ToolNames.ASSIGN_TO_TEAM, Map.of(ToolParameters.NUMBER, number,
                    ToolParameters.NOTE, AgentTexts.abbreviate(note, MAX_NOTE_LENGTH, HandOffNotes.ELLIPSIS)));
        } catch (RuntimeException unavailable) {
            throw new HandOffUnreachableException(number, note, unavailable);
        }
        if (Boolean.TRUE.equals(result.isError())) {
            var message = McpResults.textOf(result);
            if (message.startsWith(Refusals.REFUSED)) {
                // Usually a person took the incident meanwhile. Whatever the reason, an incident the agent still owns
                // goes to the default team once its claim is stale, so it is never left without an owner.
                log.warn("Incident {} was not handed to a team: {}", LogValues.safe(number), LogValues.safe(message));
                return;
            }
            throw new HandOffRejectedException(number, message);
        }
    }
}
