package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agent.constants.McpValues;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What an MCP tool returned, read as text. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class McpResults {

    /** An MCP tool result reaches us as its JSON content list; what the tool returned is the text of its content. */
    static String textOf(String mcpContent) {
        var text = new StringBuilder();
        for (var content : ToolJson.read(mcpContent)) {
            text.append(content.path(McpValues.CONTENT_TEXT).asString(""));
        }
        return text.toString();
    }

    /** The first text of a result from a tool called directly, without a model; empty when it has none. */
    static String textOf(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .findFirst()
                .orElse("");
    }
}
