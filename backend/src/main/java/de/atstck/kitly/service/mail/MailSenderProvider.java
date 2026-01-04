package de.atstck.kitly.service.mail;

public interface MailSenderProvider {
    void sendHtmlMail(String to, String toName, String subject, String htmlContent);
}

