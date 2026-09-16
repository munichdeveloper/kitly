package de.atstck.kitly.integration;

import de.atstck.kitly.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Base class for integration tests using Testcontainers.
 * Provides shared PostgreSQL container (singleton) and common test setup.
 *
 * The container is shared across all tests to improve performance and prevent
 * resource leaks from multiple container instances.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // Singleton container shared across all test classes
    private static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true); // Reuse container across test runs
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

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

    @Autowired
    protected PlanRepository planRepository;

    @Autowired
    protected DataSource dataSource;

    @BeforeEach
    void baseSetup() throws Exception {
        // The Postgres container is reused (withReuse(true)) across test runs, and
        // Flyway only (re-)applies R__seed_stripe_test_mode.sql when its checksum
        // changes - not on every run. Re-running the same idempotent upsert here
        // guarantees the known Stripe test-mode configuration for every single
        // test, regardless of what an earlier test (in this run or a previous one
        // against the reused container) may have changed in platform_settings.
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ClassPathResource("db/testdata/R__seed_stripe_test_mode.sql")));
        }
    }

    @AfterEach
    void baseTearDown() {
        // Ensure all transactions are properly closed
        // This helps prevent hanging tests
    }
}
