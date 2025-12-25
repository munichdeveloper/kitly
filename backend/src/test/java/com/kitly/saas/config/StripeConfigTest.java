package com.kitly.saas.config;

import com.kitly.saas.service.PlatformSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StripeConfigTest {

    @Mock
    private PlatformSettingService platformSettingService;

    private StripeConfig stripeConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        stripeConfig = new StripeConfig();
        stripeConfig.setPlatformSettingService(platformSettingService);
        stripeConfig.setApiKey("sk_test_default");
        stripeConfig.setWebhookSecret("whsec_default");
    }

    @Test
    void testGetPriceIdForPlan_withDynamicConfiguration() {
        // Given
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("test");
        when(platformSettingService.getSettingValue("stripe.test.api_key", null)).thenReturn("sk_test_from_db");
        when(platformSettingService.getSettingValue("stripe.test.webhook_secret", null)).thenReturn("whsec_test_from_db");
        when(platformSettingService.getSettingValue("stripe.test.plan.STARTER", null)).thenReturn("price_starter_dynamic");
        when(platformSettingService.getSettingValue("stripe.test.plan.BUSINESS", null)).thenReturn("price_business_dynamic");
        when(platformSettingService.getSettingValue("stripe.test.plan.ENTERPRISE", null)).thenReturn("price_enterprise_dynamic");

        // When
        stripeConfig.refreshStripeConfig();

        // Then
        assertEquals("price_starter_dynamic", stripeConfig.getPriceIdForPlan("STARTER"));
        assertEquals("price_business_dynamic", stripeConfig.getPriceIdForPlan("BUSINESS"));
        assertEquals("price_enterprise_dynamic", stripeConfig.getPriceIdForPlan("ENTERPRISE"));
    }

    @Test
    void testGetPriceIdForPlan_caseInsensitive() {
        // Given
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("test");
        when(platformSettingService.getSettingValue("stripe.test.plan.STARTER", null)).thenReturn("price_starter");

        // When
        stripeConfig.refreshStripeConfig();

        // Then
        assertEquals("price_starter", stripeConfig.getPriceIdForPlan("starter"));
        assertEquals("price_starter", stripeConfig.getPriceIdForPlan("STARTER"));
        assertEquals("price_starter", stripeConfig.getPriceIdForPlan("StArTeR"));
    }

    @Test
    void testGetPriceIdForPlan_notConfigured() {
        // Given
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("test");
        when(platformSettingService.getSettingValue("stripe.test.plan.STARTER", null)).thenReturn(null);

        // When
        stripeConfig.refreshStripeConfig();

        // Then - Should return null if not configured
        assertNull(stripeConfig.getPriceIdForPlan("STARTER"));
    }

    @Test
    void testGetPlanForPriceId() {
        // Given
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("test");
        when(platformSettingService.getSettingValue("stripe.test.plan.STARTER", null)).thenReturn("price_abc123");
        when(platformSettingService.getSettingValue("stripe.test.plan.BUSINESS", null)).thenReturn("price_def456");

        // When
        stripeConfig.refreshStripeConfig();

        // Then
        assertEquals("STARTER", stripeConfig.getPlanForPriceId("price_abc123"));
        assertEquals("BUSINESS", stripeConfig.getPlanForPriceId("price_def456"));
        assertNull(stripeConfig.getPlanForPriceId("price_unknown"));
    }

    @Test
    void testGetAllPlanPrices() {
        // Given
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("test");
        when(platformSettingService.getSettingValue("stripe.test.plan.STARTER", null)).thenReturn("price_starter");
        when(platformSettingService.getSettingValue("stripe.test.plan.BUSINESS", null)).thenReturn("price_business");
        when(platformSettingService.getSettingValue("stripe.test.plan.PREMIUM", null)).thenReturn("price_premium");

        // When
        stripeConfig.refreshStripeConfig();
        Map<String, String> allPrices = stripeConfig.getAllPlanPrices();

        // Then
        assertEquals(3, allPrices.size()); // Only configured plans: STARTER, BUSINESS, PREMIUM
        assertEquals("price_starter", allPrices.get("STARTER"));
        assertEquals("price_business", allPrices.get("BUSINESS"));
        assertEquals("price_premium", allPrices.get("PREMIUM"));
    }

    @Test
    void testGetPriceIdForPlan_withNullPlanName() {
        // When/Then
        assertNull(stripeConfig.getPriceIdForPlan(null));
    }

    @Test
    void testGetPlanForPriceId_withNullPriceId() {
        // When/Then
        assertNull(stripeConfig.getPlanForPriceId(null));
    }

    @Test
    void testGetAllPlanPrices_returnsUnmodifiableMap() {
        // Given
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("test");
        stripeConfig.refreshStripeConfig();

        // When
        Map<String, String> allPrices = stripeConfig.getAllPlanPrices();

        // Then - Should throw exception when trying to modify
        assertThrows(UnsupportedOperationException.class, () -> {
            allPrices.put("NEW_PLAN", "price_new");
        });
    }

    @Test
    void testRefreshStripeConfig_withNoPlatformSettingService() {
        // Given
        stripeConfig.setPlatformSettingService(null);

        // When
        stripeConfig.refreshStripeConfig();

        // Then - Should have no plan prices configured
        assertNull(stripeConfig.getPriceIdForPlan("STARTER"));
        assertNull(stripeConfig.getPriceIdForPlan("BUSINESS"));
        assertNull(stripeConfig.getPriceIdForPlan("ENTERPRISE"));
        assertEquals(0, stripeConfig.getAllPlanPrices().size());
    }

    @Test
    void testGetCurrentMode() {
        // Given
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("live");

        // When
        String mode = stripeConfig.getCurrentMode();

        // Then
        assertEquals("live", mode);
    }

    @Test
    void testGetCurrentMode_withNoPlatformSettingService() {
        // Given
        stripeConfig.setPlatformSettingService(null);

        // When
        String mode = stripeConfig.getCurrentMode();

        // Then - Should return default "test"
        assertEquals("test", mode);
    }
}

