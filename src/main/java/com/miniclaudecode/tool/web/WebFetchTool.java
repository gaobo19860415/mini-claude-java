package com.miniclaudecode.tool.web;

import com.miniclaudecode.tool.*;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class WebFetchTool implements Tool {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String name() { return "web_fetch"; }

    @Override
    public String description() {
        return "Fetch content from a URL and process it. " +
                "Returns clean markdown-formatted text content from the page.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "url", Map.of("type", "string",
                                "description", "The URL to fetch content from"),
                        "prompt", Map.of("type", "string",
                                "description", "What information to extract from the page")
                ),
                "required", List.of("url", "prompt")
        );
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public boolean isConcurrencySafe() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) throws Exception {
        String urlStr = (String) args.get("url");
        String prompt = (String) args.get("prompt");

        // Validate URL
        URI uri;
        try {
            uri = URI.create(urlStr);
            if (uri.getScheme() == null || !uri.getScheme().matches("https?")) {
                return ToolResult.error("Invalid URL scheme (must be http/https): " + urlStr);
            }
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid URL: " + urlStr);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "MiniClaudeCode/0.1")
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 400) {
                return ToolResult.error("HTTP " + status + " from " + urlStr);
            }
            String body = response.body();
            // Truncate large responses
            int maxLen = 30000;
            if (body.length() > maxLen) {
                body = body.substring(0, maxLen) + "\n\n[Content truncated at " + maxLen + " chars]";
            }
            // Strip HTML tags with basic regex
            String cleaned = body.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "")
                    .replaceAll("<style[^>]*>[\\s\\S]*?</style>", "")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s{2,}", "\n")
                    .trim();
            return ToolResult.ok("Content from " + urlStr + ":\n\n" + cleaned);
        } catch (Exception e) {
            return ToolResult.error("Failed to fetch " + urlStr + ": " + e.getMessage());
        }
    }
}
