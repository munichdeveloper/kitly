package de.atstck.kitly.repository;

import de.atstck.kitly.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    boolean existsByStripeInvoiceId(String stripeInvoiceId);
    Optional<Invoice> findByStripeInvoiceId(String stripeInvoiceId);
    List<Invoice> findByEmailSentFalseAndEmailScheduledAtBefore(LocalDateTime dateTime);
}
