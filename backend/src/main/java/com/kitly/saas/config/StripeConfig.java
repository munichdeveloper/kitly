package com.kitly.saas.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
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

    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;
    }

}