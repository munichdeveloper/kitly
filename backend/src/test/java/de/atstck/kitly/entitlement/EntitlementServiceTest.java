package de.atstck.kitly.entitlement;

import de.atstck.kitly.common.exception.ResourceNotFoundException;
import de.atstck.kitly.entity.*;
import de.atstck.kitly.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {
    
    @Mock
    private SubscriptionRepository subscriptionRepository;
    
    @Mock
    private EntitlementRepository entitlementRepository;
    
    @Mock
    private EntitlementVersionRepository entitlementVersionRepository;
    
    @Mock
    private MembershipRepository membershipRepository;
    
    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PlanService planService;
    
    @InjectMocks
    private EntitlementService entitlementService;
    
    private UUID testTenantId;
    private Tenant testTenant;
    private Subscription testSubscription;
    private PlanEntity mockPlan;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        
        testTenant = Tenant.builder()
                .id(testTenantId)
                .name("Test Tenant")
                .slug("test-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();

        mockPlan = PlanEntity.builder()
                .id(UUID.randomUUID())
                .code("business")
                .name("Business Plan")
                .build();

        testSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .plan(mockPlan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .maxSeats(50)
                .build();
    }
    
    @Test
    void testComputeEntitlements_WithProPlan() {
        // Given
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(subscriptionRepository.findByTenantIdAndStatus(testTenantId, Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(testSubscription));

        // Mock PlanService to return business plan entitlements
        Map<String, String> businessEntitlements = new LinkedHashMap<>();
        businessEntitlements.put("features.ai_assistant", "true");
        businessEntitlements.put("app_access.nim", "true");
        businessEntitlements.put("limits.projects", "100");
        businessEntitlements.put("limits.api_calls_per_month", "10000");
        when(planService.getPlanEntitlements("business")).thenReturn(businessEntitlements);

        when(entitlementRepository.findByTenantAndEnabled(testTenant, true))
                .thenReturn(Collections.emptyList());
        when(membershipRepository.countByTenantIdAndStatus(testTenantId, Membership.MembershipStatus.ACTIVE))
                .thenReturn(7L);
        
        EntitlementVersion version = EntitlementVersion.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .version(15L)
                .build();
        when(entitlementVersionRepository.findByTenant(testTenant))
                .thenReturn(Optional.of(version));
        
        // When
        EntitlementResponse response = entitlementService.computeEntitlements(testTenantId);
        
        // Then
        assertNotNull(response);
        assertEquals(testTenantId, response.getTenantId());
        assertEquals("business", response.getPlanCode());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(50, response.getSeatsQuantity());
        assertEquals(7L, response.getActiveSeats());
        assertEquals(15L, response.getEntitlementVersion());
        
        // Check plan entitlements
        List<EntitlementResponse.EntitlementItem> items = response.getItems();
        assertNotNull(items);
        assertEquals(4, items.size());

        // Verify specific entitlements
        assertTrue(items.stream().anyMatch(item -> 
                "features.ai_assistant".equals(item.getKey()) && "true".equals(item.getValue())));
        assertTrue(items.stream().anyMatch(item ->
                "app_access.nim".equals(item.getKey()) && "true".equals(item.getValue())));
        assertTrue(items.stream().anyMatch(item ->
                "limits.projects".equals(item.getKey()) && "100".equals(item.getValue())));
        assertTrue(items.stream().anyMatch(item -> 
                "limits.api_calls_per_month".equals(item.getKey()) && "10000".equals(item.getValue())));
    }
    
    @Test
    void testComputeEntitlements_WithOverrides() {
        // Given
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(subscriptionRepository.findByTenantIdAndStatus(testTenantId, Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(testSubscription));
        
        // Mock PlanService to return business plan entitlements
        Map<String, String> businessEntitlements = new LinkedHashMap<>();
        businessEntitlements.put("features.ai_assistant", "true");
        businessEntitlements.put("app_access.nim", "true");
        businessEntitlements.put("limits.projects", "100");
        businessEntitlements.put("limits.api_calls_per_month", "10000");
        when(planService.getPlanEntitlements("business")).thenReturn(businessEntitlements);

        // Add an override for ai_assistant
        Entitlement override = Entitlement.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .featureKey("features.ai_assistant")
                .featureType(Entitlement.FeatureType.BOOLEAN)
                .enabled(false)
                .build();
        
        when(entitlementRepository.findByTenantAndEnabled(testTenant, true))
                .thenReturn(Collections.singletonList(override));
        when(membershipRepository.countByTenantIdAndStatus(testTenantId, Membership.MembershipStatus.ACTIVE))
                .thenReturn(5L);
        
        EntitlementVersion version = EntitlementVersion.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .version(20L)
                .build();
        when(entitlementVersionRepository.findByTenant(testTenant))
                .thenReturn(Optional.of(version));
        
        // When
        EntitlementResponse response = entitlementService.computeEntitlements(testTenantId);
        
        // Then
        assertNotNull(response);
        
        // Check that override is applied
        Optional<EntitlementResponse.EntitlementItem> aiAssistant = response.getItems().stream()
                .filter(item -> "features.ai_assistant".equals(item.getKey()))
                .findFirst();
        
        assertTrue(aiAssistant.isPresent());
        assertEquals("false", aiAssistant.get().getValue());
        assertEquals("OVERRIDE", aiAssistant.get().getSource());
    }
    
    @Test
    void testComputeEntitlements_WithStarterPlan() {
        // Given
        PlanEntity starterPlan = PlanEntity.builder()
                .id(UUID.randomUUID())
                .code("starter")
                .name("Starter Plan")
                .build();
        testSubscription.setPlan(starterPlan);

        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(subscriptionRepository.findByTenantIdAndStatus(testTenantId, Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(testSubscription));

        // Mock PlanService to return starter plan entitlements
        Map<String, String> starterEntitlements = new LinkedHashMap<>();
        starterEntitlements.put("features.ai_assistant", "false");
        starterEntitlements.put("limits.projects", "10");
        starterEntitlements.put("limits.api_calls_per_month", "1000");
        when(planService.getPlanEntitlements("starter")).thenReturn(starterEntitlements);

        when(entitlementRepository.findByTenantAndEnabled(testTenant, true))
                .thenReturn(Collections.emptyList());
        when(membershipRepository.countByTenantIdAndStatus(testTenantId, Membership.MembershipStatus.ACTIVE))
                .thenReturn(3L);
        
        EntitlementVersion version = EntitlementVersion.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .version(1L)
                .build();
        when(entitlementVersionRepository.findByTenant(testTenant))
                .thenReturn(Optional.of(version));
        
        // When
        EntitlementResponse response = entitlementService.computeEntitlements(testTenantId);
        
        // Then
        assertEquals("starter", response.getPlanCode());
        
        // Verify starter plan entitlements
        Optional<EntitlementResponse.EntitlementItem> aiAssistant = response.getItems().stream()
                .filter(item -> "features.ai_assistant".equals(item.getKey()))
                .findFirst();
        
        assertTrue(aiAssistant.isPresent());
        assertEquals("false", aiAssistant.get().getValue());
    }
    
    @Test
    void testComputeEntitlements_NoActiveSubscription() {
        // Given
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(subscriptionRepository.findByTenantIdAndStatus(testTenantId, Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findByTenantIdAndStatus(testTenantId, Subscription.SubscriptionStatus.TRIALING))
                .thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> {
            entitlementService.computeEntitlements(testTenantId);
        });
    }
    
    @Test
    void testComputeEntitlements_CreatesVersionIfNotExists() {
        // Given
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(subscriptionRepository.findByTenantIdAndStatus(testTenantId, Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(testSubscription));

        // Mock PlanService to return business plan entitlements
        Map<String, String> businessEntitlements = new LinkedHashMap<>();
        businessEntitlements.put("features.ai_assistant", "true");
        businessEntitlements.put("app_access.nim", "true");
        businessEntitlements.put("limits.projects", "100");
        businessEntitlements.put("limits.api_calls_per_month", "10000");
        when(planService.getPlanEntitlements("business")).thenReturn(businessEntitlements);

        when(entitlementRepository.findByTenantAndEnabled(testTenant, true))
                .thenReturn(Collections.emptyList());
        when(membershipRepository.countByTenantIdAndStatus(testTenantId, Membership.MembershipStatus.ACTIVE))
                .thenReturn(1L);
        
        // No existing version
        when(entitlementVersionRepository.findByTenant(testTenant))
                .thenReturn(Optional.empty());
        
        EntitlementVersion newVersion = EntitlementVersion.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .version(1L)
                .build();
        when(entitlementVersionRepository.save(any(EntitlementVersion.class)))
                .thenReturn(newVersion);
        
        // When
        EntitlementResponse response = entitlementService.computeEntitlements(testTenantId);
        
        // Then
        assertNotNull(response);
        assertEquals(1L, response.getEntitlementVersion());
        verify(entitlementVersionRepository, times(1)).save(any(EntitlementVersion.class));
    }
    
    @Test
    void testBumpEntitlementVersion_ExistingVersion() {
        // Given
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        
        EntitlementVersion version = EntitlementVersion.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .version(10L)
                .build();
        when(entitlementVersionRepository.findByTenant(testTenant))
                .thenReturn(Optional.of(version));
        
        // When
        entitlementService.bumpEntitlementVersion(testTenantId);
        
        // Then
        assertEquals(11L, version.getVersion());
        verify(entitlementVersionRepository, times(1)).save(version);
    }
    
    @Test
    void testBumpEntitlementVersion_CreatesNewVersion() {
        // Given
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(entitlementVersionRepository.findByTenant(testTenant))
                .thenReturn(Optional.empty());
        
        EntitlementVersion newVersion = EntitlementVersion.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .version(1L)
                .build();
        when(entitlementVersionRepository.save(any(EntitlementVersion.class)))
                .thenReturn(newVersion);
        
        // When
        entitlementService.bumpEntitlementVersion(testTenantId);
        
        // Then
        verify(entitlementVersionRepository, times(2)).save(any(EntitlementVersion.class));
    }
    
    @Test
    void testBumpEntitlementVersion_TenantNotFound() {
        // Given
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> {
            entitlementService.bumpEntitlementVersion(testTenantId);
        });
    }
}
