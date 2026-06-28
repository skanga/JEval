package dev.jeval;

import java.net.URI;
import java.net.URISyntaxException;

public record McpResourceCall(
        String uri,
        Object result) {

    public McpResourceCall {
        if (uri == null) {
            throw new IllegalArgumentException("'uri' must be a URL");
        }
        if ("file:".equals(uri)) {
            uri = "file:///";
        }
        try {
            var parsed = new URI(uri);
            if (parsed.getScheme() == null) {
                throw new IllegalArgumentException("'uri' must be a URL");
            }
            if (("http".equals(parsed.getScheme()) || "https".equals(parsed.getScheme()))
                    && parsed.getHost() == null) {
                throw new IllegalArgumentException("'uri' must be a URL");
            }
            if (parsed.getRawAuthority() != null
                    && (parsed.getRawPath() == null || parsed.getRawPath().isEmpty())) {
                uri = parsed.getScheme() + "://" + parsed.getRawAuthority() + "/"
                        + (parsed.getRawQuery() == null ? "" : "?" + parsed.getRawQuery())
                        + (parsed.getRawFragment() == null ? "" : "#" + parsed.getRawFragment());
            }
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("'uri' must be a URL", error);
        }
    }
}
