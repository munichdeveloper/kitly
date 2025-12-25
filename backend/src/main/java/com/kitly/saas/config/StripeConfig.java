package com.kitly.saas.config;

import com.kitly.saas.service.PlatformSettingService;
import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Getter
@Setter
@Slf4j
public class StripeConfig {

    @Value("${stripe.api-key}")
    private String apiKey;

    @Value("${stripe.webhook-secret}")
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
                String mode = platformSettingService.getSettingValue("stripe.mode", "test");
                log.info("Initializing Stripe with mode: {}", mode);

                String modeApiKey = platformSettingService.getSettingValue("stripe." + mode + ".api_key", null);
                String modeWebhookSecret = platformSettingService.getSettingValue("stripe." + mode + ".webhook_secret", null);

                if (modeApiKey != null && !modeApiKey.isEmpty()) {
                    this.apiKey = modeApiKey;
                }
                if (modeWebhookSecret != null && !modeWebhookSecret.isEmpty()) {
                    this.webhookSecret = modeWebhookSecret;
                }

                // Load dynamic plan prices
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
     * Load dynamic plan prices from platform settings
     * Format: stripe.{mode}.plan.{PLAN_NAME} = price_id
     */
    private void loadDynamicPlanPrices(String mode) {
        if (platformSettingService == null) {
            return;
        }

        try {
            // Get all settings that match the pattern stripe.{mode}.plan.*
            String prefix = "stripe." + mode + ".plan.";

            // Try to get all common plan names
            String[] possiblePlans = {"STARTER", "BUSINESS", "ENTERPRISE", "FREE", "BASIC", "PRO", "PREMIUM"};

            for (String planName : possiblePlans) {
                String settingKey = prefix + planName;
                String priceId = platformSettingService.getSettingValue(settingKey, null);

                if (priceId != null && !priceId.isEmpty()) {
                    planPriceMap.put(planName.toUpperCase(), priceId);
                    log.debug("Loaded dynamic plan price: {} -> {}", planName, priceId);
                }
            }
        } catch (Exception e) {
            log.warn("Error loading dynamic plan prices", e);
        }
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
            return platformSettingService.getSettingValue("stripe.mode", "test");
        }
        return "test";
    }

}