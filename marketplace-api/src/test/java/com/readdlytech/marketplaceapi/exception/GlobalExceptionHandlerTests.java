package com.readdlytech.marketplaceapi.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void errorTimestampsUseUtcRegardlessOfServerTimezone() {
        var previous = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("GMT+12:00"));
            var before = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
            var database = handler.handleDatabaseUnavailable(new DataAccessResourceFailureException("offline"));
            var generic = handler.handleUnexpected(new Exception("internal"));
            var after = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
            for (var response : java.util.List.of(database, generic)) {
                assertFalse(response.getBody().timestamp().isBefore(before));
                assertFalse(response.getBody().timestamp().isAfter(after));
            }
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }

    @Test
    void gatewayTimeoutAndRateLimitResponsesAreRetryable() {
        for (var status : java.util.List.of(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)) {
            var response = handler.handleResponseStatus(new org.springframework.web.server.ResponseStatusException(status));
            assertEquals(status.value(), response.getStatusCode().value());
            assertTrue(response.getBody().retryable());
            assertEquals("5", response.getHeaders().getFirst("Retry-After"));
        }
    }

    @Test
    void databaseOutageIsRetryableWithoutLeakingConnectionDetails() {
        var response = handler.handleDatabaseUnavailable(
                new DataAccessResourceFailureException("jdbc:mysql://private-host secret"));
        assertEquals(503, response.getStatusCode().value());
        assertEquals("5", response.getHeaders().getFirst("Retry-After"));
        assertEquals("DATABASE_UNAVAILABLE", response.getBody().code());
        assertTrue(response.getBody().retryable());
        assertFalse(response.getBody().message().contains("private-host"));
    }

    @Test
    void authorizationAndValidationAreNotTransientFailures() {
        var denied = handler.handleAccessDenied(new AccessDeniedException("internal detail"));
        assertEquals(403, denied.getStatusCode().value());
        assertFalse(denied.getBody().retryable());
        assertNull(denied.getHeaders().getFirst("Retry-After"));
        var invalid = handler.handleBadRequest(new IllegalArgumentException("Invalid price"));
        assertEquals(400, invalid.getStatusCode().value());
        assertFalse(invalid.getBody().retryable());
    }
}
