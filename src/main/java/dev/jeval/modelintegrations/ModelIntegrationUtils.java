package dev.jeval.modelintegrations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.Utils;

public final class ModelIntegrationUtils {
    private static final int URL_MAX = 200;
    private static final int JSON_MAX = Math.max(Utils.lenLong(), 400);
    private static final ObjectMapper JSON = new ObjectMapper();

    private ModelIntegrationUtils() {
    }

    public static String compactDump(Object value) {
        try {
            return Utils.shorten(JSON.writeValueAsString(Utils.makeJsonSerializable(value)), JSON_MAX);
        } catch (JsonProcessingException error) {
            return Utils.shorten(String.valueOf(value), JSON_MAX);
        }
    }

    public static String fmtUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        if (url.startsWith("data:")) {
            return "[data-uri]";
        }
        return Utils.shorten(url, URL_MAX);
    }
}
