package de.atstck.kitly.service;

import de.atstck.kitly.entity.NewsletterSubscription;
import de.atstck.kitly.repository.NewsletterSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NewsletterService {

    private static final Logger logger = LoggerFactory.getLogger(NewsletterService.class);

    private final NewsletterSubscriptionRepository subscriptionRepository;
    private final EmailService emailService;

    public NewsletterService(NewsletterSubscriptionRepository subscriptionRepository, EmailService emailService) {
        this.subscriptionRepository = subscriptionRepository;
        this.emailService = emailService;
    }

    /**
     * Abonniert einen User für einen Newsletter-Kanal
     *
     * @param email E-Mail-Adresse des Abonnenten
     * @param channel Kanal (z.B. "product_updates", "marketing", "technical")
     * @param firstName Vorname (optional)
     * @param lastName Nachname (optional)
     * @param locale Sprache (optional, z.B. "de_DE")
     * @return Die erstellte oder aktualisierte Subscription (noch nicht bestätigt)
     */
    @Transactional
    public NewsletterSubscription subscribe(String email, String channel, String firstName, String lastName, String locale) {
        Optional<NewsletterSubscription> existingOpt = subscriptionRepository.findByEmailAndChannel(email, channel);

        if (existingOpt.isPresent()) {
            NewsletterSubscription existing = existingOpt.get();
            if (existing.isActive() && existing.isConfirmed()) {
                logger.info("User {} already subscribed and confirmed to channel {}", email, channel);
                return existing;
            } else if (!existing.isConfirmed()) {
                // Noch nicht bestätigt - neuen Confirmation-Token generieren und E-Mail erneut senden
                existing.setConfirmationToken(UUID.randomUUID().toString());
                existing.setSubscribedAt(LocalDateTime.now());
                if (firstName != null) existing.setFirstName(firstName);
                if (lastName != null) existing.setLastName(lastName);
                if (locale != null) existing.setLocale(locale);

                NewsletterSubscription saved = subscriptionRepository.save(existing);
                logger.info("Resending confirmation email for {} on channel {}", email, channel);

                // Bestätigungs-E-Mail senden
                sendSubscriptionConfirmation(saved);

                return saved;
            } else {
                // War abgemeldet, reaktiviere und sende neue Bestätigung
                existing.setActive(true);
                existing.setConfirmed(false);
                existing.setConfirmedAt(null);
                existing.setSubscribedAt(LocalDateTime.now());
                existing.setUnsubscribedAt(null);
                existing.setUnsubscribeToken(UUID.randomUUID().toString());
                existing.setConfirmationToken(UUID.randomUUID().toString());
                if (firstName != null) existing.setFirstName(firstName);
                if (lastName != null) existing.setLastName(lastName);
                if (locale != null) existing.setLocale(locale);

                NewsletterSubscription saved = subscriptionRepository.save(existing);
                logger.info("Reactivated newsletter subscription for {} on channel {} - awaiting confirmation", email, channel);

                // Bestätigungs-E-Mail senden
                sendSubscriptionConfirmation(saved);

                return saved;
            }
        }

        NewsletterSubscription subscription = NewsletterSubscription.builder()
                .email(email)
                .channel(channel)
                .firstName(firstName)
                .lastName(lastName)
                .locale(locale != null ? locale : "de_DE")
                .active(true)
                .confirmed(false)
                .subscribedAt(LocalDateTime.now())
                .unsubscribeToken(UUID.randomUUID().toString())
                .confirmationToken(UUID.randomUUID().toString())
                .build();

        NewsletterSubscription saved = subscriptionRepository.save(subscription);
        logger.info("Created new newsletter subscription for {} on channel {} - awaiting confirmation", email, channel);

        // Bestätigungs-E-Mail senden
        sendSubscriptionConfirmation(saved);

        return saved;
    }

    /**
     * Meldet einen User von einem Newsletter-Kanal ab
     *
     * @param email E-Mail-Adresse
     * @param channel Kanal
     * @return true wenn erfolgreich abgemeldet
     */
    @Transactional
    public boolean unsubscribe(String email, String channel) {
        Optional<NewsletterSubscription> subscriptionOpt = subscriptionRepository.findByEmailAndChannel(email, channel);

        if (subscriptionOpt.isPresent() && subscriptionOpt.get().isActive()) {
            NewsletterSubscription subscription = subscriptionOpt.get();
            subscription.setActive(false);
            subscription.setUnsubscribedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
            logger.info("Unsubscribed {} from channel {}", email, channel);
            return true;
        }

        return false;
    }

    /**
     * Bestätigt ein Newsletter-Abonnement via Confirmation-Token (Double Opt-In)
     *
     * @param token Confirmation-Token aus der E-Mail
     * @return true wenn erfolgreich bestätigt
     */
    @Transactional
    public boolean confirmSubscription(String token) {
        Optional<NewsletterSubscription> subscriptionOpt = subscriptionRepository.findByConfirmationToken(token);

        if (subscriptionOpt.isPresent()) {
            NewsletterSubscription subscription = subscriptionOpt.get();

            if (subscription.isConfirmed()) {
                logger.info("Subscription for {} on channel {} was already confirmed",
                        subscription.getEmail(), subscription.getChannel());
                return true;
            }

            subscription.setConfirmed(true);
            subscription.setConfirmedAt(LocalDateTime.now());
            subscription.setActive(true);
            subscriptionRepository.save(subscription);

            logger.info("Confirmed newsletter subscription for {} on channel {}",
                    subscription.getEmail(), subscription.getChannel());
            return true;
        }

        logger.warn("Invalid or expired confirmation token: {}", token);
        return false;
    }

    /**
     * Meldet einen User über den Unsubscribe-Token ab
     *
     * @param token Unsubscribe-Token
     * @return true wenn erfolgreich abgemeldet
     */
    @Transactional
    public boolean unsubscribeByToken(String token) {
        Optional<NewsletterSubscription> subscriptionOpt = subscriptionRepository.findByUnsubscribeToken(token);

        if (subscriptionOpt.isPresent() && subscriptionOpt.get().isActive()) {
            NewsletterSubscription subscription = subscriptionOpt.get();
            subscription.setActive(false);
            subscription.setUnsubscribedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
            logger.info("Unsubscribed {} from channel {} via token", subscription.getEmail(), subscription.getChannel());
            return true;
        }

        return false;
    }

    /**
     * Gibt alle aktiven und bestätigten Abonnements für einen Kanal zurück
     *
     * @param channel Kanal
     * @return Liste der aktiven und bestätigten Subscriptions
     */
    public List<NewsletterSubscription> getActiveSubscriptionsByChannel(String channel) {
        return subscriptionRepository.findByChannelAndActiveTrue(channel).stream()
                .filter(NewsletterSubscription::isConfirmed)
                .toList();
    }

    /**
     * Gibt alle aktiven und bestätigten Abonnements für eine E-Mail-Adresse zurück
     *
     * @param email E-Mail-Adresse
     * @return Liste der aktiven und bestätigten Subscriptions
     */
    public List<NewsletterSubscription> getActiveSubscriptionsByEmail(String email) {
        return subscriptionRepository.findByEmailAndActiveTrue(email).stream()
                .filter(NewsletterSubscription::isConfirmed)
                .toList();
    }

    /**
     * Prüft ob ein User für einen Kanal abonniert und bestätigt ist
     *
     * @param email E-Mail-Adresse
     * @param channel Kanal
     * @return true wenn aktives und bestätigtes Abonnement existiert
     */
    public boolean isSubscribed(String email, String channel) {
        Optional<NewsletterSubscription> subscription = subscriptionRepository.findByEmailAndChannel(email, channel);
        return subscription.isPresent() && subscription.get().isActive() && subscription.get().isConfirmed();
    }

    /**
     * Sendet eine Bestätigungs-E-Mail nach dem Abonnieren (Double Opt-In)
     */
    private void sendSubscriptionConfirmation(NewsletterSubscription subscription) {
        try {
            String displayName = subscription.getFirstName() != null ? subscription.getFirstName() : subscription.getEmail();
            String locale = subscription.getLocale() != null ? subscription.getLocale() : "de_DE";

            emailService.sendNewsletterConfirmation(
                subscription.getEmail(),
                displayName,
                subscription.getChannel(),
                subscription.getConfirmationToken(),
                locale
            );

            logger.info("Sent confirmation email to {} for channel {}",
                    subscription.getEmail(), subscription.getChannel());

        } catch (Exception e) {
            logger.error("Failed to send subscription confirmation to {}", subscription.getEmail(), e);
            // Nicht werfen, damit die Subscription trotzdem gespeichert wird
        }
    }
}
