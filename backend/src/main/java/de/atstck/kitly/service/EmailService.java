package de.atstck.kitly.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailTemplateService templateService;

    @Value("${app.email.from:noreply@kitly.com}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.frontend-app-url}")
    private String frontendAppUrl;

    @Value("${app.email.locale:de_DE}")
    private String defaultLocale;

    @Value("${app.email.branding:kitly}")
    private String defaultBranding;

    public EmailService(JavaMailSender mailSender, EmailTemplateService templateService) {
        this.mailSender = mailSender;
        this.templateService = templateService;
    }

    public void sendVerificationEmail(String toEmail, String token, String username) {
        sendVerificationEmail(toEmail, token, username, defaultLocale, defaultBranding);
    }

    public void sendVerificationEmail(String toEmail, String token, String username, String locale) {
        sendVerificationEmail(toEmail, token, username, locale, defaultBranding);
    }

    public void sendVerificationEmail(String toEmail, String token, String username, String locale, String branding) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Bestätige deine E-Mail-Adresse");

            // Die URL zur E-Mail-Verifizierung muss gegen das Landing Page Frontend zeigen
            String verificationUrl = frontendUrl + "/signup/verify-email?token=" + token;

            String template = templateService.loadVerificationTemplate(locale, branding);
            String htmlContent = templateService.replacePlaceholders(template, Map.of(
                    "username", username,
                    "verificationUrl", verificationUrl
            ));

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Verification email sent to: {} (locale: {}, branding: {})", toEmail, locale, branding);

        } catch (MessagingException e) {
            log.error("Failed to send verification email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    public void sendPasswordResetEmail(String toEmail, String token, String username) {
        sendPasswordResetEmail(toEmail, token, username, defaultLocale, defaultBranding);
    }

    public void sendPasswordResetEmail(String toEmail, String token, String username, String locale) {
        sendPasswordResetEmail(toEmail, token, username, locale, defaultBranding);
    }

    public void sendPasswordResetEmail(String toEmail, String token, String username, String locale, String branding) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Passwort zurücksetzen");

            // Die URL zum Zurücksetzen des Passworts muss gegen das App-Frontend zeigen
            String resetUrl = frontendAppUrl + "/reset-password?token=" + token;

            String template = templateService.loadPasswordResetTemplate(locale, branding);
            String htmlContent = templateService.replacePlaceholders(template, Map.of(
                    "username", username,
                    "resetUrl", resetUrl
            ));

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Password reset email sent to: {} (locale: {}, branding: {})", toEmail, locale, branding);

        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }
}
