package com.kitly.saas.service;

import com.kitly.saas.repository.EmailVerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service zum Bereinigen abgelaufener E-Mail-Verifizierungs-Tokens
 */
@Service
public class EmailVerificationCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(EmailVerificationCleanupService.class);

    private final EmailVerificationTokenRepository tokenRepository;

    public EmailVerificationCleanupService(EmailVerificationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Bereinigt abgelaufene Tokens täglich um 2 Uhr morgens
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        logger.info("Starting cleanup of expired email verification tokens...");

        try {
            LocalDateTime now = LocalDateTime.now();
            tokenRepository.deleteByExpiryDateBefore(now);

            logger.info("Successfully cleaned up expired email verification tokens");
        } catch (Exception e) {
            logger.error("Error during cleanup of expired tokens", e);
        }
    }
}

