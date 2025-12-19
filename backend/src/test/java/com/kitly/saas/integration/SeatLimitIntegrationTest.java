package com.kitly.saas.integration;

import com.kitly.saas.entity.*;
import com.kitly.saas.integration.builder.UserTestBuilder;
import com.kitly.saas.integration.builder.TenantTestBuilder;
import com.kitly.saas.integration.builder.MembershipTestBuilder;
import com.kitly.saas.integration.builder.SubscriptionTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for seat limits and subscription constraints.
 */
public class SeatLimitIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    private Role userRole;
    private User owner;
    private Tenant tenant;
    private Subscription subscription;
    
    @BeforeEach
    void setUp() {
        // Clean up data
        membershipRepository.deleteAll();
        subscriptionRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
        
        // Get or create default role
        userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(Role.RoleName.ROLE_USER);
                    return roleRepository.save(role);
                });
        
        // Create owner user
        owner = UserTestBuilder.aUser()
                .withUsername("owner")
                .withEmail("owner@example.com")
                .withRole(userRole)
                .build(passwordEncoder);
        owner = userRepository.save(owner);
        
        // Create tenant
        tenant = TenantTestBuilder.aTenant()
                .withName("Test Company")
                .withSlug("test-company")
                .withOwner(owner)
                .build();
        tenant = tenantRepository.save(tenant);
        
        // Create OWNER membership
        Membership ownerMembership = MembershipTestBuilder.aMembership()
                .withTenant(tenant)
                .withUser(owner)
                .withRole(Membership.MembershipRole.OWNER)
                .build();
        membershipRepository.save(ownerMembership);
    }
    
    @Test
    void whenSubscriptionHasSeatLimit_thenCanCountActiveMembers() {
        // Given - Subscription with 3 seats
        subscription = SubscriptionTestBuilder.aSubscription()
                .withTenant(tenant)
                .withPlan(Subscription.SubscriptionPlan.FREE)
                .withStatus(Subscription.SubscriptionStatus.ACTIVE)
                .withMaxSeats(3)
                .build();
        subscriptionRepository.save(subscription);
        
        // When - Count active members (currently 1: owner)
        long activeCount = membershipRepository.countByTenantIdAndStatus(
                tenant.getId(), 
                Membership.MembershipStatus.ACTIVE
        );
        
        // Then - Verify count is correct
        assertThat(activeCount).isEqualTo(1);
        assertThat(subscription.getMaxSeats()).isEqualTo(3);
        assertThat(activeCount).isLessThan(subscription.getMaxSeats());
    }
    
    @Test
    void whenSeatsAreFull_thenCanDetectLimit() {
        // Given - Subscription with 3 seats, already has 3 members
        subscription = SubscriptionTestBuilder.aSubscription()
                .withTenant(tenant)
                .withPlan(Subscription.SubscriptionPlan.FREE)
                .withStatus(Subscription.SubscriptionStatus.ACTIVE)
                .withMaxSeats(3)
                .build();
        subscriptionRepository.save(subscription);
        
        // Add 2 more members (owner already exists)
        for (int i = 1; i <= 2; i++) {
            User member = UserTestBuilder.aUser()
                    .withUsername("member" + i)
                    .withEmail("member" + i + "@example.com")
                    .withRole(userRole)
                    .build(passwordEncoder);
            member = userRepository.save(member);
            
            Membership membership = MembershipTestBuilder.aMembership()
                    .withTenant(tenant)
                    .withUser(member)
                    .withRole(Membership.MembershipRole.MEMBER)
                    .build();
            membershipRepository.save(membership);
        }
        
        // When - Count active members
        long activeCount = membershipRepository.countByTenantIdAndStatus(
                tenant.getId(), 
                Membership.MembershipStatus.ACTIVE
        );
        
        // Then - Verify seats are full
        assertThat(activeCount).isEqualTo(3);
        assertThat(activeCount).isEqualTo(subscription.getMaxSeats());
        assertThat(activeCount >= subscription.getMaxSeats()).isTrue(); // Seats are full
    }
    
    @Test
    void whenSubscriptionHasUnlimitedSeats_thenNoLimit() {
        // Given - Enterprise subscription with unlimited seats
        subscription = SubscriptionTestBuilder.aSubscription()
                .withTenant(tenant)
                .withPlan(Subscription.SubscriptionPlan.ENTERPRISE)
                .withStatus(Subscription.SubscriptionStatus.ACTIVE)
                .withMaxSeats(null) // Unlimited
                .build();
        subscriptionRepository.save(subscription);
        
        // When - Check if unlimited
        Subscription savedSubscription = subscriptionRepository.findById(subscription.getId()).orElseThrow();
        
        // Then - Verify unlimited seats
        assertThat(savedSubscription.getMaxSeats()).isNull();
    }
    
    @Test
    void whenMemberDeactivated_thenSeatIsFreed() {
        // Given - Subscription with 3 seats, 3 members
        subscription = SubscriptionTestBuilder.aSubscription()
                .withTenant(tenant)
                .withPlan(Subscription.SubscriptionPlan.FREE)
                .withStatus(Subscription.SubscriptionStatus.ACTIVE)
                .withMaxSeats(3)
                .build();
        subscriptionRepository.save(subscription);
        
        User member1 = UserTestBuilder.aUser()
                .withUsername("member1")
                .withEmail("member1@example.com")
                .withRole(userRole)
                .build(passwordEncoder);
        member1 = userRepository.save(member1);
        
        Membership membership1 = MembershipTestBuilder.aMembership()
                .withTenant(tenant)
                .withUser(member1)
                .withRole(Membership.MembershipRole.MEMBER)
                .build();
        membership1 = membershipRepository.save(membership1);
        
        // Initial count
        long initialCount = membershipRepository.countByTenantIdAndStatus(
                tenant.getId(), 
                Membership.MembershipStatus.ACTIVE
        );
        assertThat(initialCount).isEqualTo(2); // owner + member1
        
        // When - Deactivate member
        membership1.setStatus(Membership.MembershipStatus.INACTIVE);
        membershipRepository.save(membership1);
        
        // Then - Verify active count decreased
        long afterDeactivation = membershipRepository.countByTenantIdAndStatus(
                tenant.getId(), 
                Membership.MembershipStatus.ACTIVE
        );
        assertThat(afterDeactivation).isEqualTo(1); // only owner
    }
    
    @Test
    void whenMemberSuspended_thenNotCountedInActiveSeats() {
        // Given - Member with SUSPENDED status
        subscription = SubscriptionTestBuilder.aSubscription()
                .withTenant(tenant)
                .withPlan(Subscription.SubscriptionPlan.FREE)
                .withStatus(Subscription.SubscriptionStatus.ACTIVE)
                .withMaxSeats(3)
                .build();
        subscriptionRepository.save(subscription);
        
        User member = UserTestBuilder.aUser()
                .withUsername("member1")
                .withEmail("member1@example.com")
                .withRole(userRole)
                .build(passwordEncoder);
        member = userRepository.save(member);
        
        Membership membership = MembershipTestBuilder.aMembership()
                .withTenant(tenant)
                .withUser(member)
                .withRole(Membership.MembershipRole.MEMBER)
                .withStatus(Membership.MembershipStatus.SUSPENDED)
                .build();
        membershipRepository.save(membership);
        
        // When - Count only ACTIVE members
        long activeCount = membershipRepository.countByTenantIdAndStatus(
                tenant.getId(), 
                Membership.MembershipStatus.ACTIVE
        );
        
        // Then - Suspended member is not counted
        assertThat(activeCount).isEqualTo(1); // only owner
    }
    
    @Test
    void whenDifferentPlans_thenDifferentSeatLimits() {
        // Test different plan seat limits
        
        // FREE plan - 3 seats
        Subscription freeSub = SubscriptionTestBuilder.aSubscription()
                .withTenant(tenant)
                .withPlan(Subscription.SubscriptionPlan.FREE)
                .build();
        assertThat(freeSub.getMaxSeats()).isEqualTo(3);
        
        // Create a new tenant for STARTER plan
        User owner2 = UserTestBuilder.aUser()
                .withUsername("owner2")
                .withEmail("owner2@example.com")
                .withRole(userRole)
                .build(passwordEncoder);
        owner2 = userRepository.save(owner2);
        
        Tenant tenant2 = TenantTestBuilder.aTenant()
                .withName("Test Company 2")
                .withSlug("test-company-2")
                .withOwner(owner2)
                .build();
        tenant2 = tenantRepository.save(tenant2);
        
        // STARTER plan - 10 seats
        Subscription starterSub = SubscriptionTestBuilder.aSubscription()
                .withTenant(tenant2)
                .withPlan(Subscription.SubscriptionPlan.STARTER)
                .build();
        assertThat(starterSub.getMaxSeats()).isEqualTo(10);
    }
}
