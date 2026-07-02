package dev.jeval.modelintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelIntegrationUtilsTest {

    @Test
    void fmtUrlMatchesDeepEvalModelIntegrationUtility() {
        assertEquals("", ModelIntegrationUtils.fmtUrl(null));
        assertEquals("", ModelIntegrationUtils.fmtUrl(""));
        assertEquals("[data-uri]", ModelIntegrationUtils.fmtUrl("data:image/png;base64,abc"));
        assertEquals("https://example.test/image.png", ModelIntegrationUtils.fmtUrl("https://example.test/image.png"));
    }

    @Test
    void compactDumpSerializesJsonWithoutSpacesLikeDeepEval() {
        var value = new LinkedHashMap<String, Object>();
        value.put("input", "h\u00e9llo");
        value.put("ok", true);

        assertEquals("{\"input\":\"h\u00e9llo\",\"ok\":true}", ModelIntegrationUtils.compactDump(value));
    }
}
