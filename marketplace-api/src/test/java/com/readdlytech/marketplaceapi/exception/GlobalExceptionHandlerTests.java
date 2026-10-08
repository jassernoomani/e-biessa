package com.readdlytech.marketplaceapi.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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
