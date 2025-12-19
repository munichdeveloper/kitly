package com.kitly.saas.tenant.invite.mail;

/**
 * Interface for sending invitation emails.
 * Implementations should handle the actual email delivery mechanism.
 */
public interface MailSender {
    
    /**
     * Sends an invitation email to the specified recipient.
     * 
     * @param email The recipient's email address
     * @param token The invitation token (plain text, not hashed)
     * @param tenantName The name of the tenant inviting the user
     */
    void sendInvite(String email, String token, String tenantName);
}
