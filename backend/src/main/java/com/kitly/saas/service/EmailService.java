package com.kitly.saas.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.email.from:noreply@kitly.com}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String toEmail, String token, String username) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Bestätigen Sie Ihre E-Mail-Adresse");

            String verificationUrl = frontendUrl + "/auth/verify-email?token=" + token;

            String htmlContent = buildVerificationEmailHtml(username, verificationUrl);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Verification email sent to: {}", toEmail);

        } catch (MessagingException e) {
            logger.error("Failed to send verification email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    private String buildVerificationEmailHtml(String username, String verificationUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4F46E5; color: white; padding: 20px; text-align: center; }
                    .content { padding: 30px; background-color: #f9fafb; }
                    .button {
                        display: inline-block;
                        padding: 12px 30px;
                        background-color: #4F46E5;
                        color: white;
                        text-decoration: none;
                        border-radius: 5px;
                        margin: 20px 0;
                    }
                    .footer { padding: 20px; text-align: center; font-size: 12px; color: #666; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Willkommen bei Kitly!</h1>
                    </div>
                    <div class="content">
                        <h2>Hallo %s,</h2>
                        <p>vielen Dank für Ihre Registrierung bei Kitly. Um Ihr Konto zu aktivieren, bestätigen Sie bitte Ihre E-Mail-Adresse.</p>
                        <p>Klicken Sie auf den folgenden Button, um Ihre E-Mail-Adresse zu bestätigen:</p>
                        <center>
                            <a href="%s" class="button">E-Mail-Adresse bestätigen</a>
                        </center>
                        <p>Oder kopieren Sie diesen Link in Ihren Browser:</p>
                        <p style="word-break: break-all; color: #4F46E5;">%s</p>
                        <p><strong>Dieser Link ist 24 Stunden gültig.</strong></p>
                        <p>Falls Sie sich nicht bei Kitly registriert haben, können Sie diese E-Mail ignorieren.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; 2025 Kitly. Alle Rechte vorbehalten.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, verificationUrl, verificationUrl);
    }
}

