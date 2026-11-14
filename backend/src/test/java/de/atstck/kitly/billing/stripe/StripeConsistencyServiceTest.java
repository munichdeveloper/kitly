package de.atstck.kitly.billing.stripe;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import de.atstck.kitly.billing.stripe.StripeConsistencyReport.CheckResult;
import de.atstck.kitly.billing.stripe.StripeConsistencyReport.Status;
import de.atstck.kitly.billing.stripe.StripePriceGateway.PriceSnapshot;
import de.atstck.kitly.config.StripeConfig;
import de.atstck.kitly.entitlement.PlanService;
import de.atstck.kitly.entity.PlanEntity;
import de.atstck.kitly.service.PlatformSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeConsistencyServiceTest {

    private static final String TEST_KEY = "sk_test_51abcdefghijklmnop";
    private static final String LIVE_KEY = "sk_live_51abcdefghijklmnop";

    @Mock
    private StripeConfig stripeConfig;

    @Mock
    private PlatformSettingService platformSettingService;

    @Mock
    private PlanService planService;

    @Mock
    private StripePriceGateway priceGateway;

    private StripeConsistencyService service;

    @BeforeEach
    void setUp() {
        lenient().when(platformSettingService.getSettingsValuesByPrefix("stripe.plan.")).thenReturn(Map.of());
        lenient().when(planService.getAllPlans()).thenReturn(List.of(plan("CONTROLA_SOLO", true)));
        service = new StripeConsistencyService(stripeConfig, platformSettingService, planService, priceGateway,
                Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC), true);
    }

    private void configure(String mode, String apiKey, String webhookSecret, Map<String, String> prices) {
        when(stripeConfig.getCurrentMode()).thenReturn(mode);
        lenient().when(stripeConfig.getApiKey()).thenReturn(apiKey);
        lenient().when(stripeConfig.getWebhookSecret()).thenReturn(webhookSecret);
        lenient().when(stripeConfig.getAllPlanPrices()).thenReturn(prices);
    }

    private static PlanEntity plan(String code, boolean active) {
        return PlanEntity.builder().code(code).name(code).isActive(active).build();
    }

    @Test
    void consistentTestSetup_isGreenAndAllowsCheckout() throws Exception {
        configure("test", TEST_KEY, "whsec_abc", Map.of("CONTROLA_SOLO", "price_solo"));
        when(priceGateway.retrievePrice("price_solo", TEST_KEY)).thenReturn(new PriceSnapshot("price_solo", true, false));

        StripeConsistencyReport report = service.runCheck();

        assertThat(report.status()).isEqualTo(Status.GREEN);
        assertThat(report.mode()).isEqualTo("test");
        assertThat(report.errors()).isEmpty();
        assertThat(report.plans()).singleElement()
                .satisfies(p -> assertThat(p.result()).isEqualTo(CheckResult.OK));
        assertThatCode(service::assertCheckoutAllowed).doesNotThrowAnyException();
    }

    @Test
    void restrictedLiveKey_isAcceptedInLiveMode() throws Exception {
        configure("live", "rk_live_abc", "whsec_abc", Map.of("CONTROLA_SOLO", "price_solo"));
        when(priceGateway.retrievePrice("price_solo", "rk_live_abc")).thenReturn(new PriceSnapshot("price_solo", true, true));

        assertThat(service.runCheck().status()).isEqualTo(Status.GREEN);
    }

    @Test
    void liveModeWithTestPrice_isRedAndBlocksCheckout() throws Exception {
        // Acceptance criterion from #49: live mode combined with a test price ID
        configure("live", LIVE_KEY, "whsec_abc", Map.of("CONTROLA_SOLO", "price_test_solo"));
        when(priceGateway.retrievePrice("price_test_solo", LIVE_KEY)).thenReturn(new PriceSnapshot("price_test_solo", true, false));

        StripeConsistencyReport report = service.runCheck();

        assertThat(report.status()).isEqualTo(Status.RED);
        assertThat(report.errors()).anySatisfy(e -> assertThat(e).contains("test-mode price").contains("'live'"));
        assertThatThrownBy(service::assertCheckoutAllowed)
                .isInstanceOf(StripeCheckoutBlockedException.class)
                .hasMessage(StripeConsistencyService.CHECKOUT_BLOCKED_RED_MESSAGE);
    }

    @Test
    void priceNotFoundWithActiveKey_isRed() throws Exception {
        // A test price looked up with a live key is reported by Stripe as "No such price"
        configure("live", LIVE_KEY, "whsec_abc", Map.of("CONTROLA_SOLO", "price_test_solo"));
        when(priceGateway.retrievePrice("price_test_solo", LIVE_KEY)).thenThrow(new InvalidRequestException(
                "No such price: 'price_test_solo'", "id", "req_1", "resource_missing", 404, null));

        StripeConsistencyReport report = service.runCheck();

        assertThat(report.status()).isEqualTo(Status.RED);
        assertThat(report.plans()).singleElement().satisfies(p -> {
            assertThat(p.result()).isEqualTo(CheckResult.ERROR);
            assertThat(p.message()).contains("not found");
        });
    }

    @Test
    void keyPrefixNotMatchingMode_isRedAndSkipsPriceLookups() throws Exception {
        configure("live", TEST_KEY, "whsec_abc", Map.of("CONTROLA_SOLO", "price_solo"));

        StripeConsistencyReport report = service.runCheck();

        assertThat(report.status()).isEqualTo(Status.RED);
        assertThat(report.errors()).anySatisfy(e -> assertThat(e).contains("sk_test_").contains("sk_live_"));
        // The key itself must never end up in the report
        assertThat(report.toString()).doesNotContain(TEST_KEY);
        assertThat(report.plans()).singleElement()
                .satisfies(p -> assertThat(p.result()).isEqualTo(CheckResult.SKIPPED));
        verify(priceGateway, never()).retrievePrice(anyString(), anyString());
    }

    @Test
    void missingApiKeyAndWebhookSecret_isRed() {
        configure("live", null, "", Map.of());

        StripeConsistencyReport report = service.runCheck();

        assertThat(report.status()).isEqualTo(Status.RED);
        assertThat(report.errors())
                .anySatisfy(e -> assertThat(e).contains("stripe.live.api_key"))
                .anySatisfy(e -> assertThat(e).contains("stripe.live.webhook_secret"))
                .anySatisfy(e -> assertThat(e).contains("No price IDs configured"));
    }

    @Test
    void invalidMode_isRed() {
        when(stripeConfig.getCurrentMode()).thenReturn("production");

        StripeConsistencyReport report = service.runCheck();

        assertThat(report.status()).isEqualTo(Status.RED);
        assertThat(report.errors()).singleElement().satisfies(e -> assertThat(e).contains("production"));
    }

    @Test
    void archivedPriceOfActivePlan_isRed_butOfInactivePlanOnlyWarning() throws Exception {
        when(planService.getAllPlans()).thenReturn(List.of(plan("CONTROLA_SOLO", true), plan("OLD_PLAN", false)));
        configure("test", TEST_KEY, "whsec_abc", Map.of("CONTROLA_SOLO", "price_solo", "OLD_PLAN", "price_old"));
        when(priceGateway.retrievePrice("price_solo", TEST_KEY)).thenReturn(new PriceSnapshot("price_solo", true, false));
        when(priceGateway.retrievePrice("price_old", TEST_KEY)).thenReturn(new PriceSnapshot("price_old", false, false));

        StripeConsistencyReport green = service.runCheck();
        assertThat(green.status()).isEqualTo(Status.GREEN);
        assertThat(green.warnings()).anySatisfy(w -> assertThat(w).contains("OLD_PLAN"));

        when(planService.getAllPlans()).thenReturn(List.of(plan("CONTROLA_SOLO", true), plan("OLD_PLAN", true)));
        assertThat(service.runCheck().status()).isEqualTo(Status.RED);
    }

    @Test
    void stripeUnreachable_isUnknownAndDoesNotThrow() throws Exception {
        configure("test", TEST_KEY, "whsec_abc", Map.of("CONTROLA_SOLO", "price_solo"));
        when(priceGateway.retrievePrice(anyString(), anyString())).thenThrow(new ApiConnectionException("connect timed out"));

        StripeConsistencyReport report = service.runCheck();

        assertThat(report.status()).isEqualTo(Status.UNKNOWN);
        assertThat(report.plans()).singleElement()
                .satisfies(p -> assertThat(p.result()).isEqualTo(CheckResult.UNKNOWN));
        assertThatThrownBy(service::assertCheckoutAllowed)
                .isInstanceOf(StripeCheckoutBlockedException.class)
                .hasMessage(StripeConsistencyService.CHECKOUT_BLOCKED_UNKNOWN_MESSAGE);
    }

    @Test
    void unknownStatus_isRecheckedOnCheckout() throws Exception {
        configure("test", TEST_KEY, "whsec_abc", Map.of("CONTROLA_SOLO", "price_solo"));
        when(priceGateway.retrievePrice("price_solo", TEST_KEY))
                .thenThrow(new ApiConnectionException("down"))
                .thenReturn(new PriceSnapshot("price_solo", true, false));

        assertThat(service.runCheck().status()).isEqualTo(Status.UNKNOWN);

        // Stripe is back: the checkout re-runs the check and passes
        assertThatCode(service::assertCheckoutAllowed).doesNotThrowAnyException();
        assertThat(service.getLastReport().status()).isEqualTo(Status.GREEN);
        verify(priceGateway, times(2)).retrievePrice("price_solo", TEST_KEY);
    }

    @Test
    void notRunYet_isUnknownAndCheckoutRunsTheCheck() throws Exception {
        configure("test", TEST_KEY, "whsec_abc", Map.of("CONTROLA_SOLO", "price_solo"));
        when(priceGateway.retrievePrice("price_solo", TEST_KEY)).thenReturn(new PriceSnapshot("price_solo", true, false));

        assertThat(service.getLastReport().status()).isEqualTo(Status.UNKNOWN);
        assertThatCode(service::assertCheckoutAllowed).doesNotThrowAnyException();
        assertThat(service.getLastReport().status()).isEqualTo(Status.GREEN);
    }

    @Test
    void redStatus_isNotRecheckedOnEveryCheckout() throws Exception {
        configure("live", LIVE_KEY, "whsec_abc", Map.of("CONTROLA_SOLO", "price_test_solo"));
        when(priceGateway.retrievePrice("price_test_solo", LIVE_KEY)).thenReturn(new PriceSnapshot("price_test_solo", true, false));
        service.runCheck();

        assertThatThrownBy(service::assertCheckoutAllowed).isInstanceOf(StripeCheckoutBlockedException.class);
        assertThatThrownBy(service::assertCheckoutAllowed).isInstanceOf(StripeCheckoutBlockedException.class);

        verify(priceGateway, times(1)).retrievePrice(any(), any());
    }

    @Test
    void rejectedApiKey_isRed() throws Exception {
        configure("test", TEST_KEY, "whsec_abc", Map.of("A", "price_a", "B", "price_b"));
        when(priceGateway.retrievePrice("price_a", TEST_KEY))
                .thenThrow(new AuthenticationException("Invalid API Key provided", "req_1", null, 401));

        StripeConsistencyReport report = service.runCheck();

        assertThat(report.status()).isEqualTo(Status.RED);
        assertThat(report.errors()).anySatisfy(e -> assertThat(e).contains("rejected the API key"));
        // After an auth failure the remaining prices are not queried with the same bad key
        verify(priceGateway, never()).retrievePrice("price_b", TEST_KEY);
    }

    @Test
    void legacyKeyWithoutModeCounterpart_isReportedAsWarning() throws Exception {
        configure("live", LIVE_KEY, "whsec_abc", Map.of("CONTROLA_SOLO", "price_live_solo"));
        when(priceGateway.retrievePrice("price_live_solo", LIVE_KEY)).thenReturn(new PriceSnapshot("price_live_solo", true, true));
        when(platformSettingService.getSettingsValuesByPrefix("stripe.plan.")).thenReturn(Map.of(
                "stripe.plan.controla_solo", "price_test_solo",
                "stripe.plan.controla_team", "price_test_team",
                "stripe.plan.controla_team.active", "true"));

        StripeConsistencyReport report = service.runCheck();

        assertThat(report.status()).isEqualTo(Status.GREEN);
        assertThat(report.warnings())
                .anySatisfy(w -> assertThat(w).contains("stripe.plan.controla_team").contains("stripe.live.plan.CONTROLA_TEAM"))
                .noneSatisfy(w -> assertThat(w).contains("stripe.plan.controla_solo"))
                .noneSatisfy(w -> assertThat(w).contains(".active"));
    }

    @Test
    void activeKitlyPlanWithoutPriceForMode_isWarning() throws Exception {
        when(planService.getAllPlans()).thenReturn(List.of(plan("CONTROLA_SOLO", true), plan("CONTROLA_TEAM", true)));
        configure("test", TEST_KEY, "whsec_abc", Map.of("CONTROLA_SOLO", "price_solo"));
        when(priceGateway.retrievePrice("price_solo", TEST_KEY)).thenReturn(new PriceSnapshot("price_solo", true, false));

        StripeConsistencyReport report = service.runCheck();

        assertThat(report.status()).isEqualTo(Status.GREEN);
        assertThat(report.warnings()).anySatisfy(w -> assertThat(w).contains("CONTROLA_TEAM").contains("stripe.test.plan."));
    }

    @Test
    void startupCheck_neverThrows_evenIfEverythingFails() {
        when(stripeConfig.getCurrentMode()).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> service.runCheck()).doesNotThrowAnyException();
        assertThat(service.getLastReport().status()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void describePrefix_neverRevealsTheKey() {
        assertThat(StripeConsistencyService.describePrefix("sk_live_51secret")).isEqualTo("sk_live_");
        assertThat(StripeConsistencyService.describePrefix("rk_test_51secret")).isEqualTo("rk_test_");
        assertThat(StripeConsistencyService.describePrefix("pk_51secretwithoutmode")).isEqualTo("pk_...");
    }
}
