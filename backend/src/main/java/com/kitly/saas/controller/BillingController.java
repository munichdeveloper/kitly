package com.kitly.saas.controller;

import com.kitly.saas.dto.CheckoutRequest;
import com.kitly.saas.dto.SubscriptionResponse;
import com.kitly.saas.service.StripeService;
import com.kitly.saas.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final StripeService stripeService;
    private final SubscriptionService subscriptionService;

    public BillingController(StripeService stripeService, SubscriptionService subscriptionService) {
        this.stripeService = stripeService;
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckoutSession(@Valid @RequestBody CheckoutRequest request, Authentication authentication) {
        try {
            String url = stripeService.createCheckoutSession(request.getTenantId(), authentication.getName(), request.getPlan());
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/subscription/{tenantId}")
    public ResponseEntity<?> getSubscription(@PathVariable java.util.UUID tenantId) {
        try {
            SubscriptionResponse response = subscriptionService.getCurrentSubscription(tenantId);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
