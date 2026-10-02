package io.github.exepex.commerce.agents;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

/**
 * The agents defined in {@code agents/*.md} on the classpath. Each file starts with a YAML header between {@code ---}
 * lines (the settings) followed by the instructions in Markdown. A file that is missing a setting fails at startup,
 * so a broken definition never reaches a running agent.
 */
public final class AgentDefinitions {

    private static final String LOCATION = "classpath*:agents/*.md";

    private final Map<String, AgentDefinition> byId;

    private AgentDefinitions(Map<String, AgentDefinition> byId) {
        this.byId = byId;
    }

    public static AgentDefinitions load() {
        Map<String, AgentDefinition> byId = new LinkedHashMap<>();
        try {
            for (Resource file : new PathMatchingResourcePatternResolver().getResources(LOCATION)) {
                try (InputStream content = file.getInputStream()) {
                    AgentDefinition definition = parse(file.getFilename(),
                            new String(content.readAllBytes(), StandardCharsets.UTF_8));
                    if (byId.put(definition.id(), definition) != null) {
                        throw new IllegalStateException("Agent " + definition.id() + " is defined twice");
                    }
                }
            }
        } catch (IOException unreadable) {
            throw new IllegalStateException("Could not read the agent definitions", unreadable);
        }
        return new AgentDefinitions(Map.copyOf(byId));
    }

    public AgentDefinition get(String agentId) {
        AgentDefinition definition = byId.get(agentId);
        if (definition == null) {
            throw new IllegalArgumentException("No agent is defined with id " + agentId);
        }
        return definition;
    }

    public List<AgentDefinition> all() {
        return List.copyOf(byId.values());
    }

    static AgentDefinition parse(String fileName, String text) {
        String[] parts = text.replace("\r\n", "\n").split("(?m)^---\\s*$", 3);
        if (parts.length < 3 || !parts[0].isBlank()) {
            throw new IllegalStateException(fileName + " must start with a YAML header between --- lines");
        }
        Map<String, Object> header = new Yaml().load(parts[1]);
        String id = text(fileName, header, "id");
        String model = text(fileName, header, "model");
        String effort = text(fileName, header, "effort");
        int toolCallBudget = (Integer) required(fileName, header, "tool-call-budget");
        boolean customerScoped = (Boolean) required(fileName, header, "customer-scoped");
        Map<String, Object> tools = map(fileName, header, "tools");
        return new AgentDefinition(id, model, effort, toolCallBudget, customerScoped,
                list(fileName, tools, "commerce"),
                tools.containsKey("slack") ? list(fileName, tools, "slack") : List.of(),
                requireText(fileName, "the instructions", parts[2]),
                header.containsKey("slack-step") ? text(fileName, header, "slack-step") : "");
    }

    private static Object required(String fileName, Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value == null) {
            throw new IllegalStateException(fileName + " is missing '" + key + "'");
        }
        return value;
    }

    private static String text(String fileName, Map<String, Object> values, String key) {
        return requireText(fileName, "'" + key + "'", String.valueOf(required(fileName, values, key)));
    }

    private static String requireText(String fileName, String what, String value) {
        if (value.isBlank()) {
            throw new IllegalStateException(fileName + " has an empty " + what);
        }
        return value.strip();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(String fileName, Map<String, Object> values, String key) {
        return (Map<String, Object>) required(fileName, values, key);
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(String fileName, Map<String, Object> values, String key) {
        List<String> list = (List<String>) required(fileName, values, key);
        if (list.isEmpty()) {
            throw new IllegalStateException(fileName + " lists no '" + key + "' tools");
        }
        return List.copyOf(list);
    }
}
