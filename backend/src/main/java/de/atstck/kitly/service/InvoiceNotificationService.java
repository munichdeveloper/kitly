package de.atstck.kitly.service;

import de.atstck.kitly.entity.Invoice;
import de.atstck.kitly.entity.Tenant;
import de.atstck.kitly.repository.InvoiceRepository;
import de.atstck.kitly.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceNotificationService.class);

    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;
    private final EmailService emailService;

    public InvoiceNotificationService(InvoiceRepository invoiceRepository, TenantRepository tenantRepository, EmailService emailService) {
        this.invoiceRepository = invoiceRepository;
        this.tenantRepository = tenantRepository;
        this.emailService = emailService;
    }

    @Scheduled(fixedDelay = 60000) // Check every minute
    @Transactional
    public void sendPendingInvoiceEmails() {
        List<Invoice> pendingInvoices = invoiceRepository.findByEmailSentFalseAndEmailScheduledAtBefore(LocalDateTime.now());

        if (pendingInvoices.isEmpty()) {
            return;
        }

        logger.info("Processing {} pending invoice emails", pendingInvoices.size());

        for (Invoice invoice : pendingInvoices) {
            try {
                sendInvoiceEmail(invoice);
                invoice.setEmailSent(true);
                // We keep emailScheduledAt as is, to know when it was supposed to be sent
                invoiceRepository.save(invoice);
            } catch (Exception e) {
                logger.error("Failed to send pending email for invoice {}", invoice.getStripeInvoiceId(), e);
            }
        }
    }

    private void sendInvoiceEmail(Invoice invoice) {
        String invoiceNumber = invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : invoice.getStripeInvoiceId();
        String invoicePdfUrl = invoice.getInvoicePdf();
        Long amount = invoice.getAmountDue() != null ? invoice.getAmountDue() : invoice.getAmountPaid();
        String currency = invoice.getCurrency();

        // Use invoice created_at date for display, or current date if created_at is not suitable?
        // Invoice entity has createdAt which is DB creation time.
        // We ideally want the date from Stripe, but we didn't store it in Invoice entity except indirectly.
        // Let's use createdAt for now or "today".
        String formattedDate = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy").format(
                invoice.getCreatedAt() != null ? invoice.getCreatedAt() : LocalDateTime.now()
        );

        String formattedAmount = amount != null ? String.format("%.2f", amount / 100.0) : "0.00";

        // We need to fetch owner info
        Optional<Tenant> tenantOpt = tenantRepository.findById(invoice.getTenantId());
        if (tenantOpt.isPresent() && tenantOpt.get().getOwner() != null) {
            String ownerEmail = tenantOpt.get().getOwner().getEmail();
            String ownerName = tenantOpt.get().getOwner().getUsername();
            // Try to use FirstName if available
            if (tenantOpt.get().getOwner().getFirstName() != null) {
                ownerName = tenantOpt.get().getOwner().getFirstName();
            }

            emailService.sendInvoiceEmail(
                    ownerEmail,
                    ownerName,
                    invoiceNumber,
                    invoicePdfUrl,
                    formattedAmount,
                    currency != null ? currency.toUpperCase() : "USD",
                    formattedDate
            );
            logger.info("Sent scheduled invoice email {} to owner {}", invoiceNumber, ownerEmail);
        } else {
            logger.warn("Could not determine recipient for scheduled invoice email {}", invoiceNumber);
        }
    }
}

