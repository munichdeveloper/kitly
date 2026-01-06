package de.atstck.kitly.service.mail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@ConditionalOnProperty(name = "app.email.provider", havingValue = "KITLY_MAIL")
public class KitlyRemoteMailSenderProvider implements MailSenderProvider {

    private final RestTemplate restTemplate;

    @Value("${app.email.kitly-mail.url}")
    private String serviceUrl;

    @Value("${app.email.kitly-mail.api-key}")
    private String apiKey;

    @Value("${app.email.kitly-mail.username}")
    private String username;

    @Value("${app.email.kitly-mail.password}")
    private String password;

    @Value("${app.email.from:noreply@kitly.com}")
    private String fromEmail;

    @Value("${app.email.from-name:Kitly}")
    private String fromName;

    public KitlyRemoteMailSenderProvider() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void sendHtmlMail(String to, String toName, String subject, String htmlContent) {
        log.info("Sending email to {} via KITLY_MAIL provider", to);

        EmailRequest request = new EmailRequest();
        request.setFromEmail(fromEmail);
        request.setFromName(fromName);
        request.setToEmail(to);
        request.setToName(toName);
        request.setSubject(subject);
        request.setHtmlContent(htmlContent);
        // Simple HTML to Text conversion
        request.setTextContent(htmlContent.replaceAll("<[^>]*>", ""));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-API-Key", apiKey);
        } else if (username != null && !username.isBlank()) {
            headers.setBasicAuth(username, password);
        }

        HttpEntity<EmailRequest> entity = new HttpEntity<>(request, headers);

        try {
            restTemplate.postForEntity(serviceUrl, entity, Void.class);
            log.info("Email successfully sent to {} via KITLY_MAIL", to);
        } catch (Exception e) {
            log.error("Failed to send email via KITLY_MAIL to {}", to, e);
            throw new RuntimeException("Failed to send email via external service", e);
        }
    }

    @Override
    public void sendHtmlMailWithAttachment(String to, String toName, String subject, String htmlContent, String attachmentName, byte[] attachmentData, String mimeType) {
        log.warn("Attachments are not yet supported for KITLY_MAIL provider. Sending email without attachment to {}", to);
        sendHtmlMail(to, toName, subject, htmlContent);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class EmailRequest {
        private String fromEmail;
        private String fromName;
        private String toEmail;
        private String toName;
        private String subject;
        private String htmlContent;
        private String textContent;
    }
}
