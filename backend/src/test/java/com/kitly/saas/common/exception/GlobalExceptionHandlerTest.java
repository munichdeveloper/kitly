package com.kitly.saas.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler exceptionHandler;
    
    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }
    
    @Test
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Resource not found");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(exception);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Resource not found", response.getBody().getMessage());
        assertEquals("Not Found", response.getBody().getError());
    }
    
    @Test
    void testHandleUnauthorizedException() {
        UnauthorizedException exception = new UnauthorizedException("Unauthorized access");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUnauthorizedException(exception);
        
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Unauthorized access", response.getBody().getMessage());
        assertEquals("UNAUTHORIZED", response.getBody().getCode());
        assertNotNull(response.getBody().getDetails());
    }
    
    @Test
    void testHandleTenantAccessDeniedException() {
        TenantAccessDeniedException exception = new TenantAccessDeniedException("Access denied to tenant");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTenantAccessDeniedException(exception);
        
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
        assertEquals("Access denied to tenant", response.getBody().getMessage());
        assertEquals("TENANT_ACCESS_DENIED", response.getBody().getCode());
        assertNotNull(response.getBody().getDetails());
    }
    
    @Test
    void testHandleAccessDeniedException() {
        AccessDeniedException exception = new AccessDeniedException("Access denied");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDeniedException(exception);
        
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
        assertEquals("Access denied: Insufficient permissions", response.getBody().getMessage());
        assertEquals("ACCESS_DENIED", response.getBody().getCode());
    }
    
    @Test
    void testHandleSeatLimitExceededException() {
        SeatLimitExceededException exception = new SeatLimitExceededException(5, 5);
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleSeatLimitExceededException(exception);
        
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("Seat limit exceeded"));
        assertEquals("SEAT_LIMIT_EXCEEDED", response.getBody().getCode());
        assertNotNull(response.getBody().getDetails());
    }
    
    @Test
    void testHandleBadRequestException() {
        BadRequestException exception = new BadRequestException("Bad request");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBadRequestException(exception);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Bad request", response.getBody().getMessage());
    }
    
    @Test
    void testHandleValidationExceptions() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "field", "error message");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(Collections.singletonList(fieldError));
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationExceptions(exception);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Invalid input data", response.getBody().getMessage());
        assertNotNull(response.getBody().getValidationErrors());
        assertTrue(response.getBody().getValidationErrors().containsKey("field"));
    }
    
    @Test
    void testHandleGlobalException() {
        Exception exception = new Exception("Unexpected error");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGlobalException(exception);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("Unexpected error", response.getBody().getMessage());
        assertEquals("Internal Server Error", response.getBody().getError());
    }
    
    @Test
    void testErrorResponseFormat() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Test");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(exception);
        ErrorResponse body = response.getBody();
        
        assertNotNull(body);
        assertNotNull(body.getTimestamp());
        assertNotNull(body.getStatus());
        assertNotNull(body.getError());
        assertNotNull(body.getMessage());
    }
}
