package com.kitly.saas.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final EmailTemplateService templateService;

    @Value("${app.email.from:noreply@kitly.com}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

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
            helper.setSubject("Bestätigen Sie Ihre E-Mail-Adresse");

            String verificationUrl = frontendUrl + "/signup/verify-email?token=" + token;

            String template = templateService.loadVerificationTemplate(locale, branding);
            String htmlContent = templateService.replacePlaceholders(template, Map.of(
                    "username", username,
                    "verificationUrl", verificationUrl
            ));

            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Verification email sent to: {} (locale: {}, branding: {})", toEmail, locale, branding);

        } catch (MessagingException e) {
            logger.error("Failed to send verification email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }
}

