package de.atstck.kitly.service;

import de.atstck.kitly.service.mail.MailSenderProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class EmailService {

    private final MailSenderProvider mailSenderProvider;
    private final EmailTemplateService templateService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.frontend-app-url}")
    private String frontendAppUrl;

    @Value("${app.docs-url:https://docs.kitly.com}")
    private String docsUrl;

    @Value("${app.email.locale:de_DE}")
    private String defaultLocale;

    @Value("${app.email.branding:kitly}")
    private String defaultBranding;

    public EmailService(MailSenderProvider mailSenderProvider, EmailTemplateService templateService) {
        this.mailSenderProvider = mailSenderProvider;
        this.templateService = templateService;
    }

    public void sendVerificationEmail(String toEmail, String toName, String token, String username) {
        sendVerificationEmail(toEmail, toName, token, username, defaultLocale, defaultBranding);
    }

    public void sendVerificationEmail(String toEmail, String toName, String token, String username, String locale) {
        sendVerificationEmail(toEmail, toName, token, username, locale, defaultBranding);
    }

    public void sendVerificationEmail(String toEmail, String toName, String token, String username, String locale, String branding) {
        try {
            String subject = "Bestätige deine E-Mail-Adresse";

            // Die URL zur E-Mail-Verifizierung muss gegen das Landing Page Frontend zeigen
            String verificationUrl = frontendUrl + "/signup/verify-email?token=" + token;

            String template = templateService.loadVerificationTemplate(locale, branding);
            String htmlContent = templateService.replacePlaceholders(template, Map.of(
                    "firstName", toName,
                    "verificationUrl", verificationUrl
            ));

            mailSenderProvider.sendHtmlMail(toEmail, toName, subject, htmlContent);
            log.info("Verification email sent to: {} (locale: {}, branding: {})", toEmail, locale, branding);

        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    public void sendPasswordResetEmail(String toEmail, String token, String name) {
        sendPasswordResetEmail(toEmail, token, name, defaultLocale, defaultBranding);
    }

    public void sendPasswordResetEmail(String toEmail, String token, String username, String locale) {
        sendPasswordResetEmail(toEmail, token, username, locale, defaultBranding);
    }

    public void sendPasswordResetEmail(String toEmail, String token, String name, String locale, String branding) {
        try {
            String subject = "Passwort zurücksetzen";

            // Die URL zum Zurücksetzen des Passworts muss gegen das App-Frontend zeigen
            String resetUrl = frontendAppUrl + "/reset-password?token=" + token;

            String template = templateService.loadPasswordResetTemplate(locale, branding);
            String htmlContent = templateService.replacePlaceholders(template, Map.of(
                    "firstName", name,
                    "resetUrl", resetUrl
            ));

            mailSenderProvider.sendHtmlMail(toEmail, name, subject, htmlContent);
            log.info("Password reset email sent to: {} (locale: {}, branding: {})", toEmail, locale, branding);

        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    public void sendOnboardingEmail(String toEmail, String toName, String username, String planName) {
        sendOnboardingEmail(toEmail, toName, username, planName, defaultLocale, defaultBranding);
    }

    public void sendOnboardingEmail(String toEmail, String toName, String username, String planName, String locale) {
        sendOnboardingEmail(toEmail, toName, username, planName, locale, defaultBranding);
    }

    public void sendOnboardingEmail(String toEmail, String toName, String username, String planName, String locale, String branding) {
        try {
            String subject = "Willkommen - Dein Konto ist aktiv!";

            String template = templateService.loadOnboardingTemplate(locale, branding);
            String htmlContent = templateService.replacePlaceholders(template, Map.of(
                    "username", username,
                    "firstName", toName,
                    "planName", planName,
                    "appUrl", frontendAppUrl,
                    "docsUrl", docsUrl
            ));

            mailSenderProvider.sendHtmlMail(toEmail, toName, subject, htmlContent);
            log.info("Onboarding email sent to: {} (locale: {}, branding: {}, plan: {})", toEmail, locale, branding, planName);

        } catch (Exception e) {
            log.error("Failed to send onboarding email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send onboarding email", e);
        }
    }

    public void sendInvoiceEmail(String toEmail, String toName, String invoiceNumber, String invoicePdfUrl, String amount, String currency, String date) {
        sendInvoiceEmail(toEmail, toName, invoiceNumber, invoicePdfUrl, amount, currency, date, defaultLocale, defaultBranding);
    }

    public void sendInvoiceEmail(String toEmail, String toName, String invoiceNumber, String invoicePdfUrl, String amount, String currency, String date, String locale, String branding) {
        try {
            String subject = "Deine Rechnung " + invoiceNumber;

            String template = templateService.loadInvoiceTemplate(locale, branding);
            String htmlContent = templateService.replacePlaceholders(template, Map.of(
                    "firstName", toName != null ? toName : toEmail,
                    "invoiceNumber", invoiceNumber,
                    "amount", amount,
                    "currency", currency,
                    "date", date,
                    "downloadUrl", invoicePdfUrl,
                    "contactUrl", frontendAppUrl + "/support"
            ));

            byte[] pdfContent = new RestTemplate().getForObject(invoicePdfUrl, byte[].class);

            mailSenderProvider.sendHtmlMailWithAttachment(toEmail, toName, subject, htmlContent, "invoice_" + invoiceNumber + ".pdf", pdfContent, "application/pdf");
            log.info("Invoice email sent to: {} (locale: {}, branding: {}, invoice: {})", toEmail, locale, branding, invoiceNumber);

        } catch (Exception e) {
            log.error("Failed to send invoice email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send invoice email", e);
        }
    }

    public void sendNewsletterConfirmation(String toEmail, String toName, String channel, String confirmationToken) {
        sendNewsletterConfirmation(toEmail, toName, channel, confirmationToken, defaultLocale, defaultBranding);
    }

    public void sendNewsletterConfirmation(String toEmail, String toName, String channel, String confirmationToken, String locale) {
        sendNewsletterConfirmation(toEmail, toName, channel, confirmationToken, locale, defaultBranding);
    }

    public void sendNewsletterConfirmation(String toEmail, String toName, String channel, String confirmationToken, String locale, String branding) {
        try {
            String subject = "Bestätige deine Newsletter-Anmeldung";

            String confirmationUrl = frontendUrl + "/newsletter/confirm?token=" + confirmationToken;

            String template = templateService.loadNewsletterConfirmationTemplate(locale, branding);
            String htmlContent = templateService.replacePlaceholders(template, Map.of(
                    "firstName", toName != null ? toName : toEmail,
                    "channel", channel,
                    "confirmationUrl", confirmationUrl
            ));

            mailSenderProvider.sendHtmlMail(toEmail, toName, subject, htmlContent);
            log.info("Newsletter confirmation email sent to: {} (locale: {}, branding: {}, channel: {})", toEmail, locale, branding, channel);

        } catch (Exception e) {
            log.error("Failed to send newsletter confirmation email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send newsletter confirmation email", e);
        }
    }
}
