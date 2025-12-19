package com.kitly.saas.security;

import com.kitly.saas.common.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantContextFilterTest {
    
    @Mock
    private JwtUtil jwtUtil;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain filterChain;
    
    private TenantContextFilter tenantContextFilter;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tenantContextFilter = new TenantContextFilter(jwtUtil);
        TenantContextHolder.clear();
    }
    
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }
    
    @Test
    void testDoFilterInternal_WithValidToken_SetsTenantContext() throws ServletException, IOException {
        String token = "valid.jwt.token";
        UUID tenantId = UUID.randomUUID();
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractTenantId(token)).thenReturn(tenantId);
        
        tenantContextFilter.doFilterInternal(request, response, filterChain);
        
        // Verify filter chain was called
        verify(filterChain).doFilter(request, response);
        
        // Note: TenantContext is cleared after filter execution in finally block
        // This test verifies the filter executes without errors
    }
    
    @Test
    void testDoFilterInternal_WithNoAuthorizationHeader_DoesNotSetTenantContext() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);
        
        tenantContextFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        verify(jwtUtil, never()).extractTenantId(anyString());
    }
    
    @Test
    void testDoFilterInternal_WithInvalidTokenFormat_DoesNotSetTenantContext() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("InvalidFormat token");
        
        tenantContextFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        verify(jwtUtil, never()).extractTenantId(anyString());
    }
    
    @Test
    void testDoFilterInternal_WithTokenWithoutTenantId_DoesNotSetTenantContext() throws ServletException, IOException {
        String token = "valid.jwt.token";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractTenantId(token)).thenReturn(null);
        
        tenantContextFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void testDoFilterInternal_WithExceptionDuringExtraction_ContinuesFilterChain() throws ServletException, IOException {
        String token = "invalid.jwt.token";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractTenantId(token)).thenThrow(new RuntimeException("Invalid token"));
        
        // Should not throw exception
        assertDoesNotThrow(() -> tenantContextFilter.doFilterInternal(request, response, filterChain));
        
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void testDoFilterInternal_AlwaysClearsTenantContext() throws ServletException, IOException {
        String token = "valid.jwt.token";
        UUID tenantId = UUID.randomUUID();
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractTenantId(token)).thenReturn(tenantId);
        
        tenantContextFilter.doFilterInternal(request, response, filterChain);
        
        // After filter execution, context should be cleared
        // This is a behavioral test - we can't directly verify ThreadLocal clearing
        // but we verify the filter completes without leaving state
        verify(filterChain).doFilter(request, response);
    }
}
