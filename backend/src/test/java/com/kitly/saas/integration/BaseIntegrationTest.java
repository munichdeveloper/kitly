package com.kitly.saas.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.kitly.saas.repository.*;

/**
 * Base class for integration tests using Testcontainers.
 * Provides shared PostgreSQL container and common test setup.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {
    
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Autowired
    protected UserRepository userRepository;
    
    @Autowired
    protected TenantRepository tenantRepository;
    
    @Autowired
    protected MembershipRepository membershipRepository;
    
    @Autowired
    protected SubscriptionRepository subscriptionRepository;
    
    @Autowired
    protected InvitationRepository invitationRepository;
    
    @Autowired
    protected RoleRepository roleRepository;
    
    @Autowired
    protected EntitlementVersionRepository entitlementVersionRepository;
    
    @BeforeEach
    void baseSetup() {
        // Ensure database is clean before each test
        // Repositories are injected and available for use
    }
}
