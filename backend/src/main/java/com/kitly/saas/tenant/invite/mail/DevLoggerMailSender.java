package com.kitly.saas.tenant.invite.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development implementation of MailSender that logs invite information to console.
 * This should not be used in production.
 */
@Component
public class DevLoggerMailSender implements MailSender {
    
    private static final Logger logger = LoggerFactory.getLogger(DevLoggerMailSender.class);
    
    @Override
    public void sendInvite(String email, String token, String tenantName) {
        logger.info("=== INVITATION EMAIL ===");
        logger.info("To: {}", email);
        logger.info("Tenant: {}", tenantName);
        logger.info("Invitation Token: {}", token);
        logger.info("Accept URL: http://localhost:3000/accept-invite?token={}", token);
        logger.info("========================");
    }
}
