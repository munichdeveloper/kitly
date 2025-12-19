package com.kitly.saas.repository;

import com.kitly.saas.entity.WebhookInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookInboxRepository extends JpaRepository<WebhookInbox, UUID> {
    
    Optional<WebhookInbox> findByProviderAndEventId(String provider, String eventId);
    
    List<WebhookInbox> findByStatus(WebhookInbox.WebhookStatus status);
    
    List<WebhookInbox> findByProvider(String provider);
    
    List<WebhookInbox> findByProviderAndStatus(String provider, WebhookInbox.WebhookStatus status);
}
