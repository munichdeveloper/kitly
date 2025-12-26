package com.kitly.saas.service;

import com.kitly.saas.config.StripeConfig;
import com.kitly.saas.entity.Subscription;
import com.kitly.saas.entity.Tenant;
import com.kitly.saas.entity.User;
import com.kitly.saas.repository.TenantRepository;
import com.kitly.saas.repository.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.stripe.param.billingportal.SessionCreateParams.*;

@Service
public class StripeService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final StripeConfig stripeConfig;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public StripeService(TenantRepository tenantRepository, UserRepository userRepository, StripeConfig stripeConfig) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.stripeConfig = stripeConfig;
    }

    public String createCheckoutSession(UUID tenantId, String username, Subscription.SubscriptionPlan plan) throws StripeException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String priceId = getPriceIdForPlan(plan);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(frontendUrl + "/confirm?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl + "/cancel")
                .setCustomerEmail(user.getEmail())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPrice(priceId)
                                .build()
                )
                .setSubscriptionData(
                        SessionCreateParams.SubscriptionData.builder()
                                .putMetadata("tenant_id", tenant.getId().toString())
                                .build()
                )
                .putMetadata("tenant_id", tenant.getId().toString())
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    public String createPortalSession(String customerId) throws StripeException {
        var params = builder()
                .setCustomer(customerId)
                .setReturnUrl(frontendUrl + "/confirm")
                .build();

        com.stripe.model.billingportal.Session session = com.stripe.model.billingportal.Session.create(params);
        return session.getUrl();
    }

    private String getPriceIdForPlan(Subscription.SubscriptionPlan plan) {
        String priceId = stripeConfig.getPriceIdForPlan(plan.name());
        if (priceId == null) {
            throw new IllegalArgumentException("No price ID configured for plan: " + plan);
        }
        return priceId;
    }
}

