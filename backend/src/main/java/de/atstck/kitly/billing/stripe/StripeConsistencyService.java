package de.atstck.kitly.billing.stripe;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import de.atstck.kitly.billing.stripe.StripeConsistencyReport.CheckResult;
import de.atstck.kitly.billing.stripe.StripeConsistencyReport.PlanPriceCheck;
import de.atstck.kitly.billing.stripe.StripeConsistencyReport.Status;
import de.atstck.kitly.config.StripeConfig;
import de.atstck.kitly.entitlement.PlanService;
import de.atstck.kitly.entity.PlanEntity;
import de.atstck.kitly.service.PlatformSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies that the whole Stripe setup runs consistently in either test or live mode (#49):
 * <ul>
 *     <li>{@code stripe.mode} is {@code test} or {@code live}</li>
 *     <li>API key and webhook secret for the active mode are set</li>
 *     <li>the API key prefix ({@code sk_test_}/{@code rk_test_} vs. {@code sk_live_}/{@code rk_live_}) matches the mode</li>
 *     <li>every configured price ID of the active mode exists in Stripe (queried with the active key),
 *     is active and its {@code livemode} matches the mode</li>
 * </ul>
 * The check runs at startup (asynchronously, never failing startup), whenever Stripe settings change
 * and on demand. The last result is cached; checkout is refused unless it is GREEN.
 */
@Service
@Slf4j
public class StripeConsistencyService {

    private static final List<String> TEST_KEY_PREFIXES = List.of("sk_test_", "rk_test_");
    private static final List<String> LIVE_KEY_PREFIXES = List.of("sk_live_", "rk_live_");

    static final String CHECKOUT_BLOCKED_RED_MESSAGE =
            "Checkout is currently disabled because the payment configuration is inconsistent. "
                    + "Please try again later or contact support.";
    static final String CHECKOUT_BLOCKED_UNKNOWN_MESSAGE =
            "Checkout is temporarily unavailable because the payment provider could not be verified. "
                    + "Please try again in a few minutes.";

    private final StripeConfig stripeConfig;
    private final PlatformSettingService platformSettingService;
    private final PlanService planService;
    private final StripePriceGateway priceGateway;
    private final Clock clock;
    private final boolean checkOnStartup;

    private final AtomicReference<StripeConsistencyReport> lastReport;

    @Autowired
    public StripeConsistencyService(
            StripeConfig stripeConfig,
            PlatformSettingService platformSettingService,
            PlanService planService,
            StripePriceGateway priceGateway,
            @Value("${stripe.consistency-check.on-startup:true}") boolean checkOnStartup) {
        this(stripeConfig, platformSettingService, planService, priceGateway, Clock.systemUTC(), checkOnStartup);
    }

    StripeConsistencyService(
            StripeConfig stripeConfig,
            PlatformSettingService platformSettingService,
            PlanService planService,
            StripePriceGateway priceGateway,
            Clock clock,
            boolean checkOnStartup) {
        this.stripeConfig = stripeConfig;
        this.platformSettingService = platformSettingService;
        this.planService = planService;
        this.priceGateway = priceGateway;
        this.clock = clock;
        this.checkOnStartup = checkOnStartup;
        this.lastReport = new AtomicReference<>(StripeConsistencyReport.notRunYet(safeMode()));
    }

    /**
     * Runs the check once the application is ready, in a background thread, so neither a slow nor an
     * unreachable Stripe API can delay or break startup. Until it finishes the status is UNKNOWN.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        if (!checkOnStartup) {
            log.info("Stripe consistency check on startup is disabled (stripe.consistency-check.on-startup=false)");
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                runCheck();
            } catch (Exception e) {
                // runCheck() already guards Stripe calls; this is the last line of defense for startup.
                log.error("Stripe consistency check on startup failed unexpectedly", e);
            }
        }, "stripe-consistency-check");
        thread.setDaemon(true);
        thread.start();
    }

    /** The last check result (never null). */
    public StripeConsistencyReport getLastReport() {
        return lastReport.get();
    }

    /**
     * Refuses a checkout unless the Stripe setup is verified consistent. A RED result is final until the
     * settings change or the check is re-run by an admin; an UNKNOWN result (Stripe unreachable, not run yet)
     * is re-checked right away so a transient outage heals itself.
     */
    public void assertCheckoutAllowed() {
        StripeConsistencyReport report = lastReport.get();
        if (report.status() == Status.UNKNOWN) {
            report = runCheck();
        }
        if (report.status() == Status.RED) {
            log.warn("Checkout refused: Stripe configuration is RED: {}", report.errors());
            throw new StripeCheckoutBlockedException(CHECKOUT_BLOCKED_RED_MESSAGE);
        }
        if (report.status() != Status.GREEN) {
            log.warn("Checkout refused: Stripe configuration could not be verified: {}", report.warnings());
            throw new StripeCheckoutBlockedException(CHECKOUT_BLOCKED_UNKNOWN_MESSAGE);
        }
    }

    /** Runs the check now, caches and logs the result. Never throws for Stripe/network problems. */
    public synchronized StripeConsistencyReport runCheck() {
        StripeConsistencyReport report;
        try {
            report = doCheck();
        } catch (Exception e) {
            log.error("Stripe consistency check failed unexpectedly", e);
            report = new StripeConsistencyReport(Status.UNKNOWN, safeMode(), Instant.now(clock),
                    List.of(), List.of("Consistency check failed unexpectedly: " + e.getMessage()), List.of());
        }
        lastReport.set(report);
        logReport(report);
        return report;
    }

    private StripeConsistencyReport doCheck() {
        String mode = stripeConfig.getCurrentMode();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<PlanPriceCheck> planChecks = new ArrayList<>();

        boolean validMode = StripeConfig.MODE_TEST.equals(mode) || StripeConfig.MODE_LIVE.equals(mode);
        if (!validMode) {
            errors.add("stripe.mode has the invalid value '" + mode + "' (expected 'test' or 'live').");
            return new StripeConsistencyReport(Status.RED, mode, Instant.now(clock), errors, warnings, planChecks);
        }
        boolean live = StripeConfig.MODE_LIVE.equals(mode);

        // 1. API key: set and prefix matches the mode
        String apiKey = stripeConfig.getApiKey();
        boolean apiKeyUsable = false;
        if (isBlank(apiKey)) {
            errors.add("API key for mode '" + mode + "' is not set (" + StripeConfig.apiKeySettingKey(mode) + ").");
        } else if (startsWithAny(apiKey, live ? LIVE_KEY_PREFIXES : TEST_KEY_PREFIXES)) {
            apiKeyUsable = true;
        } else {
            errors.add("API key for mode '" + mode + "' starts with '" + describePrefix(apiKey)
                    + "', expected " + String.join(" or ", live ? LIVE_KEY_PREFIXES : TEST_KEY_PREFIXES) + ".");
        }

        // 2. Webhook secret: set
        String webhookSecret = stripeConfig.getWebhookSecret();
        if (isBlank(webhookSecret)) {
            errors.add("Webhook secret for mode '" + mode + "' is not set ("
                    + StripeConfig.webhookSecretSettingKey(mode) + ").");
        } else if (!webhookSecret.startsWith("whsec_")) {
            warnings.add("Webhook secret for mode '" + mode + "' does not start with 'whsec_'.");
        }

        // 3. Price IDs of the active mode
        Map<String, String> prices = new TreeMap<>(stripeConfig.getAllPlanPrices());
        Map<String, Boolean> kitlyPlanActive = loadKitlyPlanActivity(warnings);

        if (prices.isEmpty()) {
            errors.add("No price IDs configured for mode '" + mode + "' ("
                    + StripeConfig.planPricePrefix(mode) + "<PLAN>).");
        }

        boolean authFailed = false;
        for (Map.Entry<String, String> entry : prices.entrySet()) {
            String plan = entry.getKey();
            String priceId = entry.getValue();

            if (!apiKeyUsable || authFailed) {
                planChecks.add(new PlanPriceCheck(plan, priceId, CheckResult.SKIPPED,
                        "Not checked: no usable API key for mode '" + mode + "'."));
                continue;
            }

            try {
                StripePriceGateway.PriceSnapshot price = priceGateway.retrievePrice(priceId, apiKey);
                boolean planActiveInKitly = Boolean.TRUE.equals(kitlyPlanActive.get(plan));
                if (price.livemode() != live) {
                    String message = "Price is a " + (price.livemode() ? "live" : "test")
                            + "-mode price, but stripe.mode is '" + mode + "'.";
                    planChecks.add(new PlanPriceCheck(plan, priceId, CheckResult.ERROR, message));
                    errors.add("Plan " + plan + " (" + priceId + "): " + message);
                } else if (!price.active()) {
                    String message = "Price is archived (inactive) in Stripe.";
                    if (planActiveInKitly) {
                        planChecks.add(new PlanPriceCheck(plan, priceId, CheckResult.ERROR, message));
                        errors.add("Plan " + plan + " (" + priceId + "): " + message);
                    } else {
                        planChecks.add(new PlanPriceCheck(plan, priceId, CheckResult.WARNING,
                                message + " Plan is inactive in Kitly, so this does not block checkout."));
                        warnings.add("Plan " + plan + " (" + priceId + "): " + message + " (plan inactive in Kitly)");
                    }
                } else {
                    planChecks.add(new PlanPriceCheck(plan, priceId, CheckResult.OK, "Price exists, is active and matches the mode."));
                }
            } catch (AuthenticationException e) { // includes PermissionException (restricted key without price read access)
                authFailed = true;
                String message = "Stripe rejected the API key for mode '" + mode + "': " + e.getMessage();
                errors.add(message);
                planChecks.add(new PlanPriceCheck(plan, priceId, CheckResult.ERROR, message));
            } catch (InvalidRequestException e) {
                String message = isResourceMissing(e)
                        ? "Price not found with the '" + mode + "' API key (it probably belongs to the other mode or another Stripe account)."
                        : "Stripe rejected the price lookup: " + e.getMessage();
                planChecks.add(new PlanPriceCheck(plan, priceId, CheckResult.ERROR, message));
                errors.add("Plan " + plan + " (" + priceId + "): " + message);
            } catch (ApiConnectionException e) {
                planChecks.add(new PlanPriceCheck(plan, priceId, CheckResult.UNKNOWN,
                        "Stripe is not reachable: " + e.getMessage()));
            } catch (StripeException | RuntimeException e) {
                planChecks.add(new PlanPriceCheck(plan, priceId, CheckResult.UNKNOWN,
                        "Price could not be verified: " + e.getMessage()));
            }
        }

        // 4. Hints that do not block checkout
        for (Map.Entry<String, Boolean> plan : kitlyPlanActive.entrySet()) {
            if (Boolean.TRUE.equals(plan.getValue()) && !prices.containsKey(plan.getKey())) {
                warnings.add("Plan " + plan.getKey() + " is active in Kitly but has no price ID for mode '" + mode
                        + "' (" + StripeConfig.planPricePrefix(mode) + plan.getKey() + ").");
            }
        }
        addLegacyKeyWarnings(mode, prices, warnings);

        Status status;
        if (!errors.isEmpty()) {
            status = Status.RED;
        } else if (planChecks.stream().anyMatch(c -> c.result() == CheckResult.UNKNOWN)) {
            status = Status.UNKNOWN;
            warnings.add("Some price IDs could not be verified with Stripe; checkout stays blocked until they are.");
        } else {
            status = Status.GREEN;
        }
        return new StripeConsistencyReport(status, mode, Instant.now(clock), errors, warnings, planChecks);
    }

    private Map<String, Boolean> loadKitlyPlanActivity(List<String> warnings) {
        Map<String, Boolean> result = new TreeMap<>();
        try {
            List<PlanEntity> plans = planService.getAllPlans();
            if (plans != null) {
                for (PlanEntity plan : plans) {
                    if (plan.getCode() != null) {
                        result.put(plan.getCode().toUpperCase(Locale.ROOT), Boolean.TRUE.equals(plan.getIsActive()));
                    }
                }
            }
        } catch (Exception e) {
            warnings.add("Could not load Kitly plans: " + e.getMessage());
        }
        return result;
    }

    /**
     * Before #49 price IDs were stored mode-independently as {@code stripe.plan.<PLAN>}. Those keys are no
     * longer read; point out ones that have no counterpart for the active mode so nothing gets lost silently.
     */
    private void addLegacyKeyWarnings(String mode, Map<String, String> prices, List<String> warnings) {
        try {
            Map<String, String> legacy = new HashMap<>(
                    platformSettingService.getSettingsValuesByPrefix(StripeConfig.LEGACY_PLAN_PRICE_PREFIX));
            new TreeMap<>(legacy).forEach((key, value) -> {
                if (key.endsWith(".active") || isBlank(value)) {
                    return;
                }
                String plan = key.substring(StripeConfig.LEGACY_PLAN_PRICE_PREFIX.length()).toUpperCase(Locale.ROOT);
                if (!prices.containsKey(plan)) {
                    warnings.add("Legacy setting " + key + " is no longer read; configure "
                            + StripeConfig.planPricePrefix(mode) + plan + " instead.");
                }
            });
        } catch (Exception e) {
            log.debug("Could not read legacy Stripe plan settings", e);
        }
    }

    private void logReport(StripeConsistencyReport report) {
        switch (report.status()) {
            case GREEN -> log.info("Stripe consistency check GREEN (mode={}, {} price(s) verified){}",
                    report.mode(), report.plans().size(),
                    report.warnings().isEmpty() ? "" : ", warnings: " + report.warnings());
            case RED -> log.error("Stripe consistency check RED (mode={}): errors={}, warnings={}",
                    report.mode(), report.errors(), report.warnings());
            default -> log.warn("Stripe consistency check UNKNOWN (mode={}): {}; plans={}",
                    report.mode(), report.warnings(), report.plans());
        }
    }

    private String safeMode() {
        try {
            return stripeConfig.getCurrentMode();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isResourceMissing(InvalidRequestException e) {
        return "resource_missing".equals(e.getCode())
                || (e.getStatusCode() != null && e.getStatusCode() == 404);
    }

    private static boolean startsWithAny(String value, List<String> prefixes) {
        return prefixes.stream().anyMatch(value::startsWith);
    }

    /** Describes a key by its non-secret prefix only (e.g. "sk_live_"), never the key itself. */
    static String describePrefix(String key) {
        int secondUnderscore = key.indexOf('_', key.indexOf('_') + 1);
        if (secondUnderscore > 0 && secondUnderscore <= 8) {
            return key.substring(0, secondUnderscore + 1);
        }
        return key.length() <= 3 ? "***" : key.substring(0, 3) + "...";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
