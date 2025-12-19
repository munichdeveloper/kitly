package com.kitly.saas.integration;

import com.kitly.saas.entity.*;
import com.kitly.saas.integration.builder.UserTestBuilder;
import com.kitly.saas.integration.builder.TenantTestBuilder;
import com.kitly.saas.integration.builder.MembershipTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for tenant isolation.
 * Ensures users cannot access data from tenants they don't belong to.
 */
public class TenantIsolationIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    private Role userRole;
    private User user1;
    private User user2;
    private Tenant tenant1;
    private Tenant tenant2;
    
    @BeforeEach
    void setUp() {
        // Clean up data
        membershipRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
        
        // Get or create default role
        userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(Role.RoleName.ROLE_USER);
                    return roleRepository.save(role);
                });
        
        // Create two users
        user1 = UserTestBuilder.aUser()
                .withUsername("user1")
                .withEmail("user1@example.com")
                .withRole(userRole)
                .build(passwordEncoder);
        user1 = userRepository.save(user1);
        
        user2 = UserTestBuilder.aUser()
                .withUsername("user2")
                .withEmail("user2@example.com")
                .withRole(userRole)
                .build(passwordEncoder);
        user2 = userRepository.save(user2);
        
        // Create two tenants
        tenant1 = TenantTestBuilder.aTenant()
                .withName("Company 1")
                .withSlug("company-1")
                .withOwner(user1)
                .build();
        tenant1 = tenantRepository.save(tenant1);
        
        tenant2 = TenantTestBuilder.aTenant()
                .withName("Company 2")
                .withSlug("company-2")
                .withOwner(user2)
                .build();
        tenant2 = tenantRepository.save(tenant2);
        
        // Create memberships: user1 in tenant1, user2 in tenant2
        Membership membership1 = MembershipTestBuilder.aMembership()
                .withTenant(tenant1)
                .withUser(user1)
                .withRole(Membership.MembershipRole.OWNER)
                .build();
        membershipRepository.save(membership1);
        
        Membership membership2 = MembershipTestBuilder.aMembership()
                .withTenant(tenant2)
                .withUser(user2)
                .withRole(Membership.MembershipRole.OWNER)
                .build();
        membershipRepository.save(membership2);
    }
    
    @Test
    void whenUserQueriesTenant_thenOnlySeesOwnTenants() {
        // When - Get user1's memberships
        List<Membership> user1Memberships = membershipRepository.findByUserId(user1.getId());
        
        // Then - User1 only sees tenant1
        assertThat(user1Memberships).hasSize(1);
        assertThat(user1Memberships.get(0).getTenant().getId()).isEqualTo(tenant1.getId());
        assertThat(user1Memberships.get(0).getTenant().getName()).isEqualTo("Company 1");
        
        // When - Get user2's memberships
        List<Membership> user2Memberships = membershipRepository.findByUserId(user2.getId());
        
        // Then - User2 only sees tenant2
        assertThat(user2Memberships).hasSize(1);
        assertThat(user2Memberships.get(0).getTenant().getId()).isEqualTo(tenant2.getId());
        assertThat(user2Memberships.get(0).getTenant().getName()).isEqualTo("Company 2");
    }
    
    @Test
    void whenUserAccessesOtherTenant_thenNoMembershipFound() {
        // When - Try to access user1 membership in tenant2
        Optional<Membership> membership = membershipRepository.findByTenantIdAndUserId(
                tenant2.getId(), 
                user1.getId()
        );
        
        // Then - No membership found
        assertThat(membership).isEmpty();
        
        // When - Try to access user2 membership in tenant1
        Optional<Membership> membership2 = membershipRepository.findByTenantIdAndUserId(
                tenant1.getId(), 
                user2.getId()
        );
        
        // Then - No membership found
        assertThat(membership2).isEmpty();
    }
    
    @Test
    void whenQueryingTenantMembers_thenOnlySeeMembersOfSameTenant() {
        // Given - Add another member to tenant1
        User user3 = UserTestBuilder.aUser()
                .withUsername("user3")
                .withEmail("user3@example.com")
                .withRole(userRole)
                .build(passwordEncoder);
        user3 = userRepository.save(user3);
        
        Membership membership3 = MembershipTestBuilder.aMembership()
                .withTenant(tenant1)
                .withUser(user3)
                .withRole(Membership.MembershipRole.MEMBER)
                .build();
        membershipRepository.save(membership3);
        
        // When - Query members of tenant1
        List<Membership> tenant1Members = membershipRepository.findByTenantId(tenant1.getId());
        
        // Then - Only see tenant1 members (user1, user3)
        assertThat(tenant1Members).hasSize(2);
        assertThat(tenant1Members)
                .extracting(m -> m.getUser().getId())
                .containsExactlyInAnyOrder(user1.getId(), user3.getId());
        
        // When - Query members of tenant2
        List<Membership> tenant2Members = membershipRepository.findByTenantId(tenant2.getId());
        
        // Then - Only see tenant2 members (user2)
        assertThat(tenant2Members).hasSize(1);
        assertThat(tenant2Members.get(0).getUser().getId()).isEqualTo(user2.getId());
    }
    
    @Test
    void whenUserBelongsToMultipleTenants_thenCanAccessBoth() {
        // Given - Add user1 to tenant2 as well
        Membership crossMembership = MembershipTestBuilder.aMembership()
                .withTenant(tenant2)
                .withUser(user1)
                .withRole(Membership.MembershipRole.MEMBER)
                .build();
        membershipRepository.save(crossMembership);
        
        // When - Get user1's memberships
        List<Membership> user1Memberships = membershipRepository.findByUserId(user1.getId());
        
        // Then - User1 has access to both tenants
        assertThat(user1Memberships).hasSize(2);
        assertThat(user1Memberships)
                .extracting(m -> m.getTenant().getId())
                .containsExactlyInAnyOrder(tenant1.getId(), tenant2.getId());
    }
    
    @Test
    void whenTenantDeleted_thenMembershipsAreIsolated() {
        // Given - Verify initial state
        List<Membership> allMemberships = membershipRepository.findAll();
        assertThat(allMemberships).hasSize(2);
        
        // When - Delete tenant1's memberships
        membershipRepository.deleteAll(
                membershipRepository.findByTenantId(tenant1.getId())
        );
        
        // Then - Tenant2's memberships remain unaffected
        List<Membership> remainingMemberships = membershipRepository.findAll();
        assertThat(remainingMemberships).hasSize(1);
        assertThat(remainingMemberships.get(0).getTenant().getId()).isEqualTo(tenant2.getId());
    }
    
    @Test
    void whenCountingMembers_thenIsolatedByTenant() {
        // Given - Add members to both tenants
        User user3 = UserTestBuilder.aUser()
                .withUsername("user3")
                .withEmail("user3@example.com")
                .withRole(userRole)
                .build(passwordEncoder);
        user3 = userRepository.save(user3);
        
        membershipRepository.save(MembershipTestBuilder.aMembership()
                .withTenant(tenant1)
                .withUser(user3)
                .withRole(Membership.MembershipRole.MEMBER)
                .build());
        
        User user4 = UserTestBuilder.aUser()
                .withUsername("user4")
                .withEmail("user4@example.com")
                .withRole(userRole)
                .build(passwordEncoder);
        user4 = userRepository.save(user4);
        
        membershipRepository.save(MembershipTestBuilder.aMembership()
                .withTenant(tenant1)
                .withUser(user4)
                .withRole(Membership.MembershipRole.MEMBER)
                .build());
        
        // When - Count members per tenant
        long tenant1Count = membershipRepository.countByTenantIdAndStatus(
                tenant1.getId(), 
                Membership.MembershipStatus.ACTIVE
        );
        long tenant2Count = membershipRepository.countByTenantIdAndStatus(
                tenant2.getId(), 
                Membership.MembershipStatus.ACTIVE
        );
        
        // Then - Counts are isolated
        assertThat(tenant1Count).isEqualTo(3); // user1, user3, user4
        assertThat(tenant2Count).isEqualTo(1); // user2
    }
}
