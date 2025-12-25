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

@Configuration
@Getter
@Setter
@Slf4j
public class StripeConfig {

    @Value("${stripe.api-key}")
    private String apiKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.price.starter}")
    private String starterPriceId;

    @Value("${stripe.price.business}")
    private String businessPriceId;

    @Value("${stripe.price.enterprise}")
    private String enterprisePriceId;

    @Autowired(required = false)
    private PlatformSettingService platformSettingService;

    @PostConstruct
    public void init() {
        refreshStripeConfig();
    }

    public void refreshStripeConfig() {
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

                String modeStarterPrice = platformSettingService.getSettingValue("stripe." + mode + ".price.starter", null);
                String modeBusinessPrice = platformSettingService.getSettingValue("stripe." + mode + ".price.business", null);
                String modeEnterprisePrice = platformSettingService.getSettingValue("stripe." + mode + ".price.enterprise", null);

                if (modeStarterPrice != null && !modeStarterPrice.isEmpty()) {
                    this.starterPriceId = modeStarterPrice;
                }
                if (modeBusinessPrice != null && !modeBusinessPrice.isEmpty()) {
                    this.businessPriceId = modeBusinessPrice;
                }
                if (modeEnterprisePrice != null && !modeEnterprisePrice.isEmpty()) {
                    this.enterprisePriceId = modeEnterprisePrice;
                }

            } catch (Exception e) {
                log.warn("Could not load platform settings, using default configuration", e);
            }
        }

        Stripe.apiKey = apiKey;
        log.info("Stripe configured successfully");
    }

    public String getCurrentMode() {
        if (platformSettingService != null) {
            return platformSettingService.getSettingValue("stripe.mode", "test");
        }
        return "test";
    }

}