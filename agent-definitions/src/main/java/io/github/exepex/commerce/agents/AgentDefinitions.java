package io.github.exepex.commerce.agents;

import io.github.exepex.commerce.agents.constants.DefinitionKeys;
import io.github.exepex.commerce.agents.constants.ErrorMessages;
import io.github.exepex.commerce.agents.exception.DuplicateAgentDefinitionException;
import io.github.exepex.commerce.agents.exception.InvalidAgentDefinitionException;
import io.github.exepex.commerce.agents.exception.MissingAgentDefinitionException;
import io.github.exepex.commerce.agents.exception.UnreadableAgentDefinitionsException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

/**
 * The agents defined in {@code agents/*.md} on the classpath. Each file starts with a YAML header between {@code ---}
 * lines (the settings) followed by the instructions in Markdown. A file that is missing a setting fails at startup,
 * so a broken definition never reaches a running agent.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class AgentDefinitions {

    private final Map<String, AgentDefinition> byId;

    public static AgentDefinitions load() {
        var byId = new LinkedHashMap<String, AgentDefinition>();
        try {
            for (var file : new PathMatchingResourcePatternResolver().getResources(DefinitionKeys.LOCATION)) {
                try (var content = file.getInputStream()) {
                    var definition = parse(file.getFilename(),
                            new String(content.readAllBytes(), StandardCharsets.UTF_8));
                    if (byId.put(definition.id(), definition) != null) {
                        throw new DuplicateAgentDefinitionException(definition.id());
                    }
                }
            }
        } catch (IOException unreadable) {
            throw new UnreadableAgentDefinitionsException(unreadable);
        }
        return new AgentDefinitions(Collections.unmodifiableMap(byId));
    }

    public AgentDefinition get(String agentId) {
        var definition = byId.get(agentId);
        if (definition == null) {
            throw new MissingAgentDefinitionException(agentId);
        }
        return definition;
    }

    public List<AgentDefinition> all() {
        return List.copyOf(byId.values());
    }

    static AgentDefinition parse(String fileName, String text) {
        var parts = text.replace(DefinitionKeys.WINDOWS_LINE_BREAK, DefinitionKeys.LINE_BREAK)
                .split(DefinitionKeys.HEADER_SEPARATOR, 3);
        if (parts.length < 3 || !parts[0].isBlank()) {
            throw new InvalidAgentDefinitionException(fileName, ErrorMessages.NO_HEADER);
        }
        Map<String, Object> header = new Yaml().load(parts[1]);
        var id = text(fileName, header, DefinitionKeys.ID);
        var model = text(fileName, header, DefinitionKeys.MODEL);
        var effort = text(fileName, header, DefinitionKeys.EFFORT);
        var toolCallBudget = (Integer) required(fileName, header, DefinitionKeys.TOOL_CALL_BUDGET);
        var customerScoped = (Boolean) required(fileName, header, DefinitionKeys.CUSTOMER_SCOPED);
        var tools = map(fileName, header, DefinitionKeys.TOOLS);
        return new AgentDefinition(id, model, effort, toolCallBudget, customerScoped,
                list(fileName, tools, DefinitionKeys.COMMERCE_TOOLS),
                tools.containsKey(DefinitionKeys.SLACK_TOOLS)
                        ? list(fileName, tools, DefinitionKeys.SLACK_TOOLS) : List.of(),
                tools.containsKey(DefinitionKeys.SERVICENOW_TOOLS)
                        ? list(fileName, tools, DefinitionKeys.SERVICENOW_TOOLS) : List.of(),
                requireText(fileName, ErrorMessages.INSTRUCTIONS, parts[2]),
                header.containsKey(DefinitionKeys.SLACK_STEP) ? text(fileName, header, DefinitionKeys.SLACK_STEP) : "");
    }

    private static Object required(String fileName, Map<String, Object> values, String key) {
        var value = values == null ? null : values.get(key);
        if (value == null) {
            throw new InvalidAgentDefinitionException(fileName, ErrorMessages.MISSING_SETTING.formatted(key));
        }
        return value;
    }

    private static String text(String fileName, Map<String, Object> values, String key) {
        return requireText(fileName, ErrorMessages.SETTING.formatted(key),
                String.valueOf(required(fileName, values, key)));
    }

    private static String requireText(String fileName, String what, String value) {
        if (value.isBlank()) {
            throw new InvalidAgentDefinitionException(fileName, ErrorMessages.EMPTY_SETTING.formatted(what));
        }
        return value.strip();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(String fileName, Map<String, Object> values, String key) {
        return (Map<String, Object>) required(fileName, values, key);
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(String fileName, Map<String, Object> values, String key) {
        var list = (List<String>) required(fileName, values, key);
        if (list.isEmpty()) {
            throw new InvalidAgentDefinitionException(fileName, ErrorMessages.NO_TOOLS.formatted(key));
        }
        return List.copyOf(list);
    }
}
