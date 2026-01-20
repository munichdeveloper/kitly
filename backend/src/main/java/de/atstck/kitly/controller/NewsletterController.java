package de.atstck.kitly.controller;

import de.atstck.kitly.dto.NewsletterSubscribeRequest;
import de.atstck.kitly.dto.NewsletterSubscriptionResponse;
import de.atstck.kitly.entity.NewsletterSubscription;
import de.atstck.kitly.service.NewsletterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/newsletter")
public class NewsletterController {

    private final NewsletterService newsletterService;

    public NewsletterController(NewsletterService newsletterService) {
        this.newsletterService = newsletterService;
    }

    /**
     * Newsletter abonnieren
     * POST /api/newsletter/subscribe
     */
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@Valid @RequestBody NewsletterSubscribeRequest request) {
        try {
            NewsletterSubscription subscription = newsletterService.subscribe(
                    request.getEmail(),
                    request.getChannel(),
                    request.getFirstName(),
                    request.getLastName(),
                    request.getLocale()
            );

            NewsletterSubscriptionResponse response = toResponse(subscription);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Fehler beim Abonnieren des Newsletters: " + e.getMessage()
            ));
        }
    }

    /**
     * Newsletter abmelden (mit E-Mail und Kanal)
     * POST /api/newsletter/unsubscribe
     */
    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestParam String email, @RequestParam String channel) {
        try {
            boolean success = newsletterService.unsubscribe(email, channel);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "message", "Erfolgreich vom Newsletter abgemeldet",
                        "success", true
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Kein aktives Abonnement gefunden",
                        "success", false
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Fehler beim Abmelden: " + e.getMessage(),
                    "success", false
            ));
        }
    }

    /**
     * Newsletter-Abonnement bestätigen (Double Opt-In)
     * GET /api/newsletter/confirm/{token}
     */
    @GetMapping("/confirm/{token}")
    public ResponseEntity<?> confirmSubscription(@PathVariable String token) {
        try {
            boolean success = newsletterService.confirmSubscription(token);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "message", "Ihr Newsletter-Abonnement wurde erfolgreich bestätigt",
                        "success", true
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Ungültiger oder abgelaufener Bestätigungs-Link",
                        "success", false
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Fehler bei der Bestätigung: " + e.getMessage(),
                    "success", false
            ));
        }
    }

    /**
     * Newsletter abmelden (mit Unsubscribe-Token)
     * GET /api/newsletter/unsubscribe/{token}
     */
    @GetMapping("/unsubscribe/{token}")
    public ResponseEntity<?> unsubscribeByToken(@PathVariable String token) {
        try {
            boolean success = newsletterService.unsubscribeByToken(token);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "message", "Sie wurden erfolgreich vom Newsletter abgemeldet",
                        "success", true
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Ungültiger oder bereits verwendeter Abmelde-Link",
                        "success", false
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Fehler beim Abmelden: " + e.getMessage(),
                    "success", false
            ));
        }
    }

    /**
     * Abonnement-Status prüfen
     * GET /api/newsletter/status?email=...&channel=...
     */
    @GetMapping("/status")
    public ResponseEntity<?> checkStatus(@RequestParam String email, @RequestParam String channel) {
        try {
            boolean isSubscribed = newsletterService.isSubscribed(email, channel);
            return ResponseEntity.ok(Map.of(
                    "email", email,
                    "channel", channel,
                    "subscribed", isSubscribed
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Fehler beim Prüfen des Status: " + e.getMessage()
            ));
        }
    }

    /**
     * Alle aktiven Abonnements für eine E-Mail-Adresse abrufen
     * GET /api/newsletter/subscriptions?email=...
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<?> getSubscriptionsByEmail(@RequestParam String email) {
        try {
            List<NewsletterSubscription> subscriptions = newsletterService.getActiveSubscriptionsByEmail(email);
            List<NewsletterSubscriptionResponse> responses = subscriptions.stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Fehler beim Abrufen der Abonnements: " + e.getMessage()
            ));
        }
    }

    /**
     * Alle aktiven Abonnements für einen Kanal abrufen (Admin)
     * GET /api/newsletter/channel/{channel}/subscribers
     */
    @GetMapping("/channel/{channel}/subscribers")
    public ResponseEntity<?> getSubscribersByChannel(@PathVariable String channel) {
        try {
            List<NewsletterSubscription> subscriptions = newsletterService.getActiveSubscriptionsByChannel(channel);
            List<NewsletterSubscriptionResponse> responses = subscriptions.stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(Map.of(
                    "channel", channel,
                    "count", responses.size(),
                    "subscribers", responses
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Fehler beim Abrufen der Abonnenten: " + e.getMessage()
            ));
        }
    }

    /**
     * Hilfsmethode zum Konvertieren von Entity zu Response DTO
     */
    private NewsletterSubscriptionResponse toResponse(NewsletterSubscription subscription) {
        return NewsletterSubscriptionResponse.builder()
                .id(subscription.getId())
                .email(subscription.getEmail())
                .channel(subscription.getChannel())
                .active(subscription.isActive())
                .confirmed(subscription.isConfirmed())
                .subscribedAt(subscription.getSubscribedAt())
                .confirmedAt(subscription.getConfirmedAt())
                .unsubscribedAt(subscription.getUnsubscribedAt())
                .firstName(subscription.getFirstName())
                .lastName(subscription.getLastName())
                .locale(subscription.getLocale())
                .build();
    }
}
