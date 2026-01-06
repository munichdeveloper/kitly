package de.atstck.kitly.service.mail;

public interface MailSenderProvider {
    void sendHtmlMail(String to, String toName, String subject, String htmlContent);

    void sendHtmlMailWithAttachment(String to, String toName, String subject, String htmlContent, String attachmentName, byte[] attachmentData, String mimeType);
}
