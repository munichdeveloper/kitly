package com.kitly.saas.common.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextHolderTest {
    
    private UUID testTenantId;
    private UUID testUserId;
    
    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        // Clear context before each test
        TenantContextHolder.clear();
    }
    
    @AfterEach
    void tearDown() {
        // Always clear context after tests
        TenantContextHolder.clear();
    }
    
    @Test
    void testSetAndGetContext() {
        TenantContext context = TenantContext.of(testTenantId, testUserId);
        TenantContextHolder.setContext(context);
        
        TenantContext retrievedContext = TenantContextHolder.getContext();
        
        assertNotNull(retrievedContext);
        assertEquals(testTenantId, retrievedContext.getTenantId());
        assertEquals(testUserId, retrievedContext.getUserId());
    }
    
    @Test
    void testSetAndGetTenantId() {
        TenantContextHolder.setTenantId(testTenantId);
        
        UUID retrievedTenantId = TenantContextHolder.getTenantId();
        
        assertEquals(testTenantId, retrievedTenantId);
    }
    
    @Test
    void testGetContextInitializesIfNull() {
        TenantContext context = TenantContextHolder.getContext();
        
        assertNotNull(context);
        assertNull(context.getTenantId());
    }
    
    @Test
    void testClearContext() {
        TenantContextHolder.setTenantId(testTenantId);
        assertNotNull(TenantContextHolder.getTenantId());
        
        TenantContextHolder.clear();
        
        // After clear, getting tenant ID should initialize new context
        TenantContext context = TenantContextHolder.getContext();
        assertNotNull(context);
        assertNull(context.getTenantId());
    }
    
    @Test
    void testThreadLocalIsolation() throws InterruptedException {
        UUID mainThreadTenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(mainThreadTenantId);
        
        final UUID[] otherThreadTenantId = new UUID[1];
        final UUID otherTenantId = UUID.randomUUID();
        
        Thread otherThread = new Thread(() -> {
            TenantContextHolder.setTenantId(otherTenantId);
            otherThreadTenantId[0] = TenantContextHolder.getTenantId();
        });
        
        otherThread.start();
        otherThread.join();
        
        // Main thread should still have its own tenant ID
        assertEquals(mainThreadTenantId, TenantContextHolder.getTenantId());
        // Other thread should have had its own tenant ID
        assertEquals(otherTenantId, otherThreadTenantId[0]);
    }
}
