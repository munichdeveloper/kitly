package de.atstck.kitly.config;

import de.atstck.kitly.service.PlatformSettingService;
import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Configuration
@Getter
@Setter
@Slf4j
public class StripeConfig {

    public static final String MODE_KEY = "stripe.mode";
    public static final String MODE_TEST = "test";
    public static final String MODE_LIVE = "live";
    /** Legacy, mode-independent price ID prefix (before #49). Kept in the DB, no longer read. */
    public static final String LEGACY_PLAN_PRICE_PREFIX = "stripe.plan.";

    private String apiKey;
    private String webhookSecret;

    // Dynamic plan to price ID mapping
    private Map<String, String> planPriceMap = new HashMap<>();

    @Autowired(required = false)
    private PlatformSettingService platformSettingService;

    @PostConstruct
    public void init() {
        refreshStripeConfig();
    }

    public void refreshStripeConfig() {
        // Clear the map before refreshing
        planPriceMap.clear();

        if (platformSettingService != null) {
            try {
                String mode = normalizeMode(platformSettingService.getSettingValue(MODE_KEY, MODE_TEST));
                log.info("Initializing Stripe with mode: {}", mode);

                // Credentials are strictly per mode: if the active mode has no key/secret, we must not
                // keep using the values of the previously active mode (e.g. a test key after switching to live).
                this.apiKey = blankToNull(platformSettingService.getSettingValue(apiKeySettingKey(mode), null));
                this.webhookSecret = blankToNull(platformSettingService.getSettingValue(webhookSecretSettingKey(mode), null));

                // Load dynamic plan prices for the active mode
                // Format: stripe.{mode}.plan.{PLAN_NAME} = price_id
                loadDynamicPlanPrices(mode);

            } catch (Exception e) {
                log.warn("Could not load platform settings, using default configuration", e);
            }
        }


        Stripe.apiKey = apiKey;
        log.info("Stripe configured successfully with {} plan price mappings", planPriceMap.size());
    }

    /**
     * Load dynamic plan prices for the given mode from platform settings.
     * Format: stripe.{mode}.plan.{PLAN_NAME} = price_id
     * <p>
     * The legacy, mode-independent keys {@code stripe.plan.{PLAN_NAME}} are intentionally NOT read
     * anymore: falling back to them after a switch to live mode would silently reuse test price IDs.
     * Migration V30 copied the legacy values to {@code stripe.test.plan.*}.
     */
    private void loadDynamicPlanPrices(String mode) {
        if (platformSettingService == null) {
            return;
        }

        try {
            String prefix = planPricePrefix(mode);

            Map<String, String> settings = platformSettingService.getSettingsValuesByPrefix(prefix);

            for (Map.Entry<String, String> entry : settings.entrySet()) {
                String key = entry.getKey();
                String priceId = entry.getValue();

                // Skip .active settings - those are for activation status, not price IDs
                if (key.endsWith(".active")) {
                    continue;
                }

                // Extract plan name from key: stripe.{mode}.plan.{PLAN_NAME}
                if (key.length() > prefix.length()) {
                    String planName = key.substring(prefix.length());
                    if (priceId != null && priceId.startsWith("price_")) {
                        planPriceMap.put(planName.toUpperCase(), priceId);
                        log.debug("Loaded dynamic plan price: {} -> {}", planName, priceId);
                    } else {
                        log.warn("Skipping invalid price ID for plan {}: {}", planName, priceId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error loading dynamic plan prices", e);
        }
    }

    /** Settings key prefix of the plan price IDs for the given mode, e.g. {@code stripe.live.plan.}. */
    public static String planPricePrefix(String mode) {
        return "stripe." + mode + ".plan.";
    }

    public static String apiKeySettingKey(String mode) {
        return "stripe." + mode + ".api_key";
    }

    public static String webhookSecretSettingKey(String mode) {
        return "stripe." + mode + ".webhook_secret";
    }

    /**
     * Normalizes a raw {@code stripe.mode} value (trim, lower case, blank -> test). Unknown values are
     * returned as-is so the consistency check can report them instead of silently picking a mode.
     */
    public static String normalizeMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return MODE_TEST;
        }
        return rawMode.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Get the Stripe Price ID for a given plan name
     * @param planName The plan name (e.g., "STARTER", "BUSINESS", "ENTERPRISE")
     * @return The Stripe Price ID or null if not configured
     */
    public String getPriceIdForPlan(String planName) {
        if (planName == null) {
            return null;
        }
        return planPriceMap.get(planName.toUpperCase());
    }

    /**
     * Get the plan name for a given Stripe Price ID
     * @param priceId The Stripe Price ID
     * @return The plan name or null if not found
     */
    public String getPlanForPriceId(String priceId) {
        if (priceId == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : planPriceMap.entrySet()) {
            if (entry.getValue().equals(priceId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get all configured plan to price mappings
     * @return Unmodifiable map of plan names to price IDs
     */
    public Map<String, String> getAllPlanPrices() {
        return Map.copyOf(planPriceMap);
    }

    public String getCurrentMode() {
        if (platformSettingService != null) {
            return normalizeMode(platformSettingService.getSettingValue(MODE_KEY, MODE_TEST));
        }
        return MODE_TEST;
    }

    /** True when the active mode is {@code live}, i.e. Stripe objects must have {@code livemode=true}. */
    public boolean isLiveMode() {
        return MODE_LIVE.equals(getCurrentMode());
    }

}