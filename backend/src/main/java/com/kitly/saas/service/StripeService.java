package com.kitly.saas.service;

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

@Service
public class StripeService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @Value("${stripe.price.starter}")
    private String starterPriceId;

    @Value("${stripe.price.business}")
    private String businessPriceId;

    @Value("${stripe.price.enterprise}")
    private String enterprisePriceId;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public StripeService(TenantRepository tenantRepository, UserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
    }

    public String createCheckoutSession(UUID tenantId, String username, Subscription.SubscriptionPlan plan) throws StripeException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String priceId = getPriceIdForPlan(plan);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(frontendUrl + "/dashboard?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl + "/pricing")
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
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(frontendUrl + "/dashboard")
                        .build();

        com.stripe.model.billingportal.Session session = com.stripe.model.billingportal.Session.create(params);
        return session.getUrl();
    }

    private String getPriceIdForPlan(Subscription.SubscriptionPlan plan) {
        return switch (plan) {
            case STARTER -> starterPriceId;
            case BUSINESS -> businessPriceId;
            case ENTERPRISE -> enterprisePriceId;
            default -> throw new IllegalArgumentException("Invalid plan for checkout: " + plan);
        };
    }
}

