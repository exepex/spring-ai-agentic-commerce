package io.github.exepex.commerce.agent;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** How tool calls and tool schemas are read and written as JSON. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ToolJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The arguments the model passed to a tool; a call without any reads as an empty object. */
    static JsonNode arguments(String toolInput) {
        return JSON.readTree(toolInput == null || toolInput.isBlank() ? "{}" : toolInput);
    }

    /** One text argument of a tool call, or empty when the call has none. */
    static String argument(String toolInput, String name) {
        return arguments(toolInput).path(name).asString("");
    }

    static JsonNode read(String json) {
        return JSON.readTree(json);
    }

    /** A text field of a JSON object, or empty when it has none. */
    static String textField(String json, String field) {
        return read(json).path(field).asString("");
    }

    static String write(JsonNode json) {
        return JSON.writeValueAsString(json);
    }

    /** The tool as the model sees it: the same tool without the parameters code sets. */
    static ToolDefinition without(Set<String> parameters, ToolDefinition original) {
        ObjectNode schema = (ObjectNode) JSON.readTree(original.inputSchema());
        if (schema.get("properties") instanceof ObjectNode properties) {
            parameters.forEach(properties::remove);
        }
        if (schema.get("required") instanceof ArrayNode required) {
            for (int index = required.size() - 1; index >= 0; index--) {
                if (parameters.contains(required.get(index).asString())) {
                    required.remove(index);
                }
            }
        }
        return ToolDefinition.builder()
                .name(original.name())
                .description(original.description())
                .inputSchema(JSON.writeValueAsString(schema))
                .build();
    }
}
