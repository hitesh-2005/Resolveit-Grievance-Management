package com.resolveit.grievancemanagement.exception;

import com.resolveit.grievancemanagement.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/api/test-endpoint");
        webRequest = new ServletWebRequest(servletRequest);
    }

    @Test
    @DisplayName("BadCredentialsException returns HTTP 401 Unauthorized with generic message")
    void handleBadCredentialsException_Returns401() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials in internal system");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBadCredentialsException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
        assertThat(response.getBody().getPath()).isEqualTo("/api/test-endpoint");
    }

    @Test
    @DisplayName("AccessDeniedException returns HTTP 403 Forbidden")
    void handleAccessDeniedException_Returns403() {
        AccessDeniedException ex = new AccessDeniedException("User lacks OFFICER role");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDeniedException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getMessage()).contains("Access denied");
    }

    @Test
    @DisplayName("ResourceNotFoundException returns HTTP 404 Not Found")
    void handleResourceNotFoundException_Returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Complaint not found with ID : '42'");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Complaint not found with ID : '42'");
    }

    @Test
    @DisplayName("IllegalArgumentException returns HTTP 400 Bad Request")
    void handleIllegalArgumentException_Returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Threshold hours must be greater than zero");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgumentException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Threshold hours must be greater than zero");
    }

    @Test
    @DisplayName("Uncaught generic Exception returns HTTP 500 without leaking stack trace or internal message")
    void handleGlobalException_Returns500WithoutLeakingInternals() {
        Exception internalEx = new RuntimeException("SQL syntax error near SELECT * FROM secrets_table");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGlobalException(internalEx, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getMessage())
                .as("Client error message MUST NOT reveal internal database/code specifics")
                .isEqualTo("An unexpected internal error occurred. Please contact system support.");
        assertThat(response.getBody().getMessage())
                .doesNotContain("SQL")
                .doesNotContain("secrets_table");
    }
}
