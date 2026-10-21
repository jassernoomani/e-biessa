package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.AiSearchFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class AiServiceClientTests {
    private MockRestServiceServer server;
    private AiServiceClient client;
    private final AiSearchFilter filters = new AiSearchFilter(null, null, null, null, null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = spy(RestClient.builder());
        server = MockRestServiceServer.bindTo(builder).build();
        // Preserve the mock transport when the production constructor applies timeouts.
        doReturn(builder).when(builder).requestFactory(any());
        client = new AiServiceClient(builder, "http://ai.test", "test-token", true, 120, 2, 10, 1, 1,
                new AiCircuitBreakers(3, 30));
    }

    @Test
    void emptySearchIsUnavailableRatherThanAnEmptySuccessfulResult() {
        server.expect(requestTo("http://ai.test/v1/search")).andRespond(withNoContent());
        assertUnavailable(() -> client.search("brake", 12, .5, filters));
        server.verify();
    }

    @Test
    void emptyAssistantAndReindexAreNotReportedAsSuccess() {
        server.expect(requestTo("http://ai.test/v1/assistant")).andRespond(withNoContent());
        server.expect(requestTo("http://ai.test/v1/listings/reindex")).andRespond(withNoContent());
        assertUnavailable(() -> client.assist("brake", 12, .5, filters, List.of()));
        assertUnavailable(() -> client.replaceAll(List.of()));
        server.verify();
    }

    @Test
    void upstreamFailureDoesNotLeakBodyAndNextRequestCanRecover() {
        server.expect(requestTo("http://ai.test/v1/search"))
                .andRespond(withServerError().body("private upstream details"));
        server.expect(requestTo("http://ai.test/v1/search"))
                .andExpect(header("X-AI-Service-Token", "test-token"))
                .andRespond(withSuccess("{\"query\":\"brake\",\"results\":[],\"tookMs\":1,\"extractedConstraints\":{}}", MediaType.APPLICATION_JSON));
        ResponseStatusException error = assertUnavailable(() -> client.search("brake", 12, .5, filters));
        assertFalse(error.getReason().contains("private upstream"));
        assertNotNull(client.search("brake", 12, .5, filters));
        server.verify();
    }

    private ResponseStatusException assertUnavailable(org.junit.jupiter.api.function.Executable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        return error;
    }

    @Test
    void interruptedRequestsPreserveInterruptAndDoNotCallUpstream() {
        List<org.junit.jupiter.api.function.Executable> requests = List.of(
                () -> client.search("brake", 12, .5, filters),
                () -> client.assist("brake", 12, .5, filters, List.of()),
                () -> client.identifyPartImage(new byte[]{1}, MediaType.IMAGE_JPEG));
        for (var request : requests) {
            Thread.currentThread().interrupt();
            try {
                assertUnavailable(request);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted(); // Do not leak interrupt state into subsequent tests.
            }
        }
        server.verify();
    }

    @Test
    void assistantAndVisionFailuresHideUpstreamDetails() {
        server.expect(requestTo("http://ai.test/v1/assistant"))
                .andRespond(withServerError().body("private upstream details"));
        server.expect(requestTo("http://ai.test/v1/vision/identify"))
                .andRespond(withServerError().body("private upstream details"));
        var assistant = assertUnavailable(() -> client.assist("brake", 12, .5, filters, List.of()));
        var vision = assertUnavailable(() -> client.identifyPartImage(new byte[]{1}, MediaType.IMAGE_JPEG));
        assertFalse(assistant.getReason().contains("private upstream"));
        assertFalse(vision.getReason().contains("private upstream"));
        server.verify();
    }

    @Test
    void uncertainVisionResponseKeepsItsStatusAndEmptyCandidates() {
        server.expect(requestTo("http://ai.test/v1/vision/identify"))
                .andRespond(withSuccess("{\"candidates\":[],\"model\":\"test-vision\",\"status\":\"UNCERTAIN\",\"caution\":\"Try a closer photo.\"}", MediaType.APPLICATION_JSON));
        var response = client.identifyPartImage(new byte[]{1}, MediaType.IMAGE_JPEG);
        assertEquals("UNCERTAIN", response.status());
        assertTrue(response.candidates().isEmpty());
        assertEquals("Try a closer photo.", response.caution());
        server.verify();
    }
}
