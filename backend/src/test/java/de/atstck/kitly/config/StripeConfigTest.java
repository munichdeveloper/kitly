package de.atstck.kitly.config;

import de.atstck.kitly.service.PlatformSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
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

        Map<String, String> planSettings = Map.of(
            "stripe.test.plan.STARTER", "price_starter_dynamic",
            "stripe.test.plan.BUSINESS", "price_business_dynamic",
            "stripe.test.plan.ENTERPRISE", "price_enterprise_dynamic"
        );
        when(platformSettingService.getSettingsValuesByPrefix("stripe.test.plan.")).thenReturn(planSettings);

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
        Map<String, String> planSettings = Map.of(
            "stripe.test.plan.STARTER", "price_starter"
        );
        when(platformSettingService.getSettingsValuesByPrefix("stripe.test.plan.")).thenReturn(planSettings);

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
        when(platformSettingService.getSettingsValuesByPrefix("stripe.test.plan.")).thenReturn(Map.of());

        // When
        stripeConfig.refreshStripeConfig();

        // Then - Should return null if not configured
        assertNull(stripeConfig.getPriceIdForPlan("STARTER"));
    }

    @Test
    void testGetPlanForPriceId() {
        // Given
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("test");
        Map<String, String> planSettings = Map.of(
            "stripe.test.plan.STARTER", "price_abc123",
            "stripe.test.plan.BUSINESS", "price_def456"
        );
        when(platformSettingService.getSettingsValuesByPrefix("stripe.test.plan.")).thenReturn(planSettings);

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
        Map<String, String> planSettings = Map.of(
            "stripe.test.plan.STARTER", "price_starter",
            "stripe.test.plan.BUSINESS", "price_business",
            "stripe.test.plan.PREMIUM", "price_premium"
        );
        when(platformSettingService.getSettingsValuesByPrefix("stripe.test.plan.")).thenReturn(planSettings);

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

    @Test
    void testLiveMode_readsLivePriceIdsAndCredentialsOnly() {
        // Given: live mode with its own credentials and price IDs, plus legacy and test-mode price IDs
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("live");
        when(platformSettingService.getSettingValue("stripe.live.api_key", null)).thenReturn("sk_live_abc");
        when(platformSettingService.getSettingValue("stripe.live.webhook_secret", null)).thenReturn("whsec_live");
        when(platformSettingService.getSettingsValuesByPrefix("stripe.live.plan."))
                .thenReturn(Map.of("stripe.live.plan.controla_solo", "price_live_solo"));
        lenient().when(platformSettingService.getSettingsValuesByPrefix("stripe.test.plan."))
                .thenReturn(Map.of("stripe.test.plan.controla_solo", "price_test_solo"));
        lenient().when(platformSettingService.getSettingsValuesByPrefix("stripe.plan."))
                .thenReturn(Map.of("stripe.plan.controla_solo", "price_legacy_solo"));

        // When
        stripeConfig.refreshStripeConfig();

        // Then: only the live values are used
        assertEquals("price_live_solo", stripeConfig.getPriceIdForPlan("controla_solo"));
        assertEquals(1, stripeConfig.getAllPlanPrices().size());
        assertEquals("sk_live_abc", stripeConfig.getApiKey());
        assertEquals("whsec_live", stripeConfig.getWebhookSecret());
        verify(platformSettingService, never()).getSettingsValuesByPrefix("stripe.plan.");
        verify(platformSettingService, never()).getSettingsValuesByPrefix("stripe.test.plan.");
    }

    @Test
    void testLiveModeWithoutLivePriceIds_doesNotFallBackToLegacyKeys() {
        // Given: only legacy (mode-independent) price IDs exist
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("live");
        when(platformSettingService.getSettingsValuesByPrefix("stripe.live.plan.")).thenReturn(Map.of());
        lenient().when(platformSettingService.getSettingsValuesByPrefix("stripe.plan."))
                .thenReturn(Map.of("stripe.plan.STARTER", "price_legacy_test_id"));

        // When
        stripeConfig.refreshStripeConfig();

        // Then: no price ID at all instead of a test price in live mode
        assertNull(stripeConfig.getPriceIdForPlan("STARTER"));
    }

    @Test
    void testSwitchToModeWithoutCredentials_clearsPreviousModeCredentials() {
        // Given: test mode fully configured and loaded
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("test");
        when(platformSettingService.getSettingValue("stripe.test.api_key", null)).thenReturn("sk_test_abc");
        when(platformSettingService.getSettingValue("stripe.test.webhook_secret", null)).thenReturn("whsec_test");
        when(platformSettingService.getSettingsValuesByPrefix(anyString())).thenReturn(Map.of());
        stripeConfig.refreshStripeConfig();
        assertEquals("sk_test_abc", stripeConfig.getApiKey());

        // When: switching to live without live credentials
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn("live");
        when(platformSettingService.getSettingValue("stripe.live.api_key", null)).thenReturn("");
        stripeConfig.refreshStripeConfig();

        // Then: the test credentials are not carried over into live mode
        assertNull(stripeConfig.getApiKey());
        assertNull(stripeConfig.getWebhookSecret());
    }

    @Test
    void testGetCurrentMode_isNormalized() {
        when(platformSettingService.getSettingValue("stripe.mode", "test")).thenReturn(" LIVE ");
        assertEquals("live", stripeConfig.getCurrentMode());
        assertTrue(stripeConfig.isLiveMode());
    }
}
