package de.atstck.kitly.repository;

import de.atstck.kitly.entity.NewsletterSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NewsletterSubscriptionRepository extends JpaRepository<NewsletterSubscription, UUID> {

    Optional<NewsletterSubscription> findByEmailAndChannel(String email, String channel);

    List<NewsletterSubscription> findByChannelAndActiveTrue(String channel);

    List<NewsletterSubscription> findByEmailAndActiveTrue(String email);

    Optional<NewsletterSubscription> findByUnsubscribeToken(String token);

    Optional<NewsletterSubscription> findByConfirmationToken(String token);

    boolean existsByEmailAndChannelAndActiveTrue(String email, String channel);
}
