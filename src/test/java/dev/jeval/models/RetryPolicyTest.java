package dev.jeval.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void extractsErrorCodesFromResponseBodyMarkersAndGrpcLikeCode() {
        var markers = Map.of("insufficient_quota", Set.of("insufficient_quota", "exceeded your current quota"));

        assertEquals("insufficient_quota",
                RetryPolicy.extractErrorCode(new RateLimitError(
                        new JsonResponse(Map.of("error", Map.of("code", "insufficient_quota"))),
                        null,
                        "")));
        assertEquals("throttle",
                RetryPolicy.extractErrorCode(new RateLimitError(null, Map.of("error", Map.of("code", "throttle")), "")));
        assertEquals("42",
                RetryPolicy.extractErrorCode(new RateLimitError(new JsonResponse(Map.of("error", Map.of("code", 42))), null, "")));
        assertEquals("insufficient_quota",
                RetryPolicy.extractErrorCode(new RateLimitError(new RaisingResponse(), null,
                        "You have exceeded your current quota."), markers));
        assertEquals("ThrottlingException",
                RetryPolicy.extractErrorCode(new FakeClientError(Map.of(
                        "Error", Map.of("Code", "ThrottlingException", "Message", "...")))));
        assertEquals("unavailable", RetryPolicy.extractErrorCode(new DummyGrpcError()));
        assertEquals("", RetryPolicy.extractErrorCode(new RateLimitError(null, null, "")));
    }

    @Test
    void classifiesNetworkHttpAuthAndRateLimitErrorsLikeDeepEval() {
        var policy = testPolicy(true);

        assertEquals(true, RetryPolicy.isTransient(policy, new NetTimeout()));
        assertEquals(true, RetryPolicy.isTransient(policy, new NetConn()));
        assertEquals(true, RetryPolicy.isTransient(policy, new HttpStatusError(500)));
        assertEquals(true, RetryPolicy.isTransient(policy, new HttpStatusError("500")));
        assertEquals(false, RetryPolicy.isTransient(policy, new HttpStatusError(400)));
        assertEquals(false, RetryPolicy.isTransient(policy, new AuthError()));
        assertEquals(true, RetryPolicy.isTransient(policy,
                new RateLimitError(new JsonResponse(Map.of("error", Map.of("code", "other"))), null, "")));
        assertEquals(false, RetryPolicy.isTransient(policy,
                new RateLimitError(new JsonResponse(Map.of("error", Map.of("code", "insufficient_quota"))), null, "")));
    }

    @Test
    void supportsExtraNonRetryableCodesMarkersAndDisablingServerRetries() {
        var policy = testPolicy(true);
        var customMarkers = Map.of("custom_code", Set.of("special sentinel"));

        assertEquals(false, RetryPolicy.isTransient(
                policy,
                new RateLimitError(null, Map.of("error", Map.of("code", "soft_throttle")), ""),
                Set.of("soft_throttle"),
                null));
        assertEquals("custom_code",
                RetryPolicy.extractErrorCode(new RateLimitError(null, null, "SPECIAL SENTINEL present"), customMarkers));
        assertEquals(true, RetryPolicy.isTransient(
                policy,
                new RateLimitError(null, null, "SPECIAL SENTINEL present"),
                Set.of(),
                customMarkers));
        assertEquals(false, RetryPolicy.isTransient(testPolicy(false), new HttpStatusError(500)));
    }

    @Test
    void readsRetrySettingsFromEnvironmentWithDeepEvalDefaultsAndSafeFallbacks() {
        assertEquals(new RetryPolicy.RetrySettings(2, 1.0, 2.0, 2.0, 5.0),
                RetryPolicy.retrySettings(Map.of()));

        var settings = RetryPolicy.retrySettings(Map.of(
                "DEEPEVAL_RETRY_MAX_ATTEMPTS", "3",
                "DEEPEVAL_RETRY_INITIAL_SECONDS", "0.5",
                "DEEPEVAL_RETRY_EXP_BASE", "3",
                "DEEPEVAL_RETRY_JITTER", "0",
                "DEEPEVAL_RETRY_CAP_SECONDS", "9"));

        assertEquals(3, settings.maxAttempts());
        assertEquals(0.5, settings.initialSeconds());
        assertEquals(3.0, settings.expBase());
        assertEquals(0.0, settings.jitterSeconds());
        assertEquals(9.0, settings.capSeconds());

        assertEquals(new RetryPolicy.RetrySettings(2, 1.0, 2.0, 2.0, 5.0),
                RetryPolicy.retrySettings(Map.of(
                        "DEEPEVAL_RETRY_MAX_ATTEMPTS", "0",
                        "DEEPEVAL_RETRY_INITIAL_SECONDS", "not-a-float",
                        "DEEPEVAL_RETRY_EXP_BASE", "0.5",
                        "DEEPEVAL_RETRY_JITTER", "-1",
                        "DEEPEVAL_RETRY_CAP_SECONDS", "NaN")));
    }

    @Test
    void computesDynamicWaitAndStopLikeDeepEval() {
        var settings = new RetryPolicy.RetrySettings(3, 0.5, 3.0, 0.0, 9.0);

        assertEquals(0.5, RetryPolicy.retryDelaySeconds(settings, 1));
        assertEquals(1.5, RetryPolicy.retryDelaySeconds(settings, 2));
        assertEquals(4.5, RetryPolicy.retryDelaySeconds(settings, 3));
        assertFalse(RetryPolicy.shouldStopAfterAttempt(settings, 2));
        assertTrue(RetryPolicy.shouldStopAfterAttempt(settings, 3));

        assertEquals(0.0, RetryPolicy.retryDelaySeconds(new RetryPolicy.RetrySettings(2, 1.0, 2.0, 2.0, 0.0), 1));
        assertEquals(2.0, RetryPolicy.retryDelaySeconds(new RetryPolicy.RetrySettings(2, 5.0, 2.0, 0.0, 2.0), 1));
    }

    @Test
    void sdkRetryProviderParsingAndPolicyLookupMatchDeepEval() {
        var policy = testPolicy(true);

        assertEquals(Set.of("*"),
                RetryPolicy.sdkRetryProviders(Map.of("DEEPEVAL_SDK_RETRY_PROVIDERS", "openai, *")));
        assertEquals(Set.of("openai", "azure-openai"),
                RetryPolicy.sdkRetryProviders(Map.of("DEEPEVAL_SDK_RETRY_PROVIDERS", "OpenAI; azure_openai ; openai")));

        assertTrue(RetryPolicy.sdkRetriesFor("anything", Set.of("*")));
        assertTrue(RetryPolicy.sdkRetriesFor("Azure OpenAI", Set.of("azure-openai")));
        assertFalse(RetryPolicy.sdkRetriesFor("bedrock", Set.of("openai")));

        assertNull(RetryPolicy.getRetryPolicyFor("openai", Map.of("openai", policy), Set.of("openai")));
        assertEquals(policy, RetryPolicy.getRetryPolicyFor("OpenAI", Map.of("openai", policy), Set.of()));
        assertNull(RetryPolicy.getRetryPolicyFor("missing", Map.of("openai", policy), Set.of()));
    }

    private static RetryPolicy.ErrorPolicy testPolicy(boolean retry5xx) {
        return RetryPolicy.errorPolicy(
                Set.of(AuthError.class),
                Set.of(RateLimitError.class),
                Set.of(NetTimeout.class, NetConn.class),
                Set.of(HttpStatusError.class),
                Set.of("insufficient_quota"),
                retry5xx,
                Map.of("insufficient_quota", Set.of("insufficient_quota", "exceeded your current quota")));
    }

    private static final class JsonResponse {
        private final Map<String, Object> payload;

        private JsonResponse(Map<String, Object> payload) {
            this.payload = payload;
        }

        Map<String, Object> json() {
            return payload;
        }
    }

    private static final class RaisingResponse {
        Map<String, Object> json() {
            throw new IllegalStateException("boom");
        }
    }

    private static final class AuthError extends RuntimeException {
    }

    private static final class RateLimitError extends RuntimeException {
        private final Object response;
        private final Object body;

        private RateLimitError(Object response, Object body, String message) {
            super(message);
            this.response = response;
            this.body = body;
        }
    }

    private static final class FakeClientError extends RuntimeException {
        private final Object response;

        private FakeClientError(Object response) {
            this.response = response;
        }
    }

    private static final class NetTimeout extends RuntimeException {
    }

    private static final class NetConn extends RuntimeException {
    }

    private static final class HttpStatusError extends RuntimeException {
        private final Object statusCode;

        private HttpStatusError(Object statusCode) {
            this.statusCode = statusCode;
        }
    }

    private static final class DummyGrpcStatus {
        private final String name;

        private DummyGrpcStatus(String name) {
            this.name = name;
        }
    }

    private static final class DummyGrpcError extends RuntimeException {
        DummyGrpcStatus code() {
            return new DummyGrpcStatus("UNAVAILABLE");
        }
    }
}
