package com.kitly.saas.controller;

import com.kitly.saas.dto.CheckoutRequest;
import com.kitly.saas.dto.SubscriptionResponse;
import com.kitly.saas.entity.Invoice;
import com.kitly.saas.repository.InvoiceRepository;
import com.kitly.saas.service.StripeService;
import com.kitly.saas.service.SubscriptionService;
import com.kitly.saas.security.annotation.TenantAccessCheck;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final StripeService stripeService;
    private final SubscriptionService subscriptionService;
    private final InvoiceRepository invoiceRepository;

    public BillingController(StripeService stripeService, SubscriptionService subscriptionService, InvoiceRepository invoiceRepository) {
        this.stripeService = stripeService;
        this.subscriptionService = subscriptionService;
        this.invoiceRepository = invoiceRepository;
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
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
    @TenantAccessCheck
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

    @GetMapping("/invoices/{tenantId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
    @TenantAccessCheck
    public ResponseEntity<?> getInvoices(@PathVariable java.util.UUID tenantId) {
        try {
            List<Invoice> invoices = invoiceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
            return ResponseEntity.ok(invoices);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
