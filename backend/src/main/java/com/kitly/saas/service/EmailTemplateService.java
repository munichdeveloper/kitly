package com.kitly.saas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class EmailTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(EmailTemplateService.class);

    @Value("${app.email.locale:de_DE}")
    private String defaultLocale;

    @Value("${app.email.branding:kitly}")
    private String defaultBranding;

    // Hart verdrahtete Template-Typen für Business-Logik
    private static final String VERIFICATION_TEMPLATE = "verification";
    private static final String PASSWORD_RESET_TEMPLATE = "password-reset";

    /**
     * Lädt ein E-Mail-Template basierend auf Locale, Branding und Template-Typ.
     *
     * @param locale       Die Locale (z.B. "de_DE", "en_US", "ch_DE")
     * @param branding     Das Branding (z.B. "kitly", "external-app-1")
     * @param templateType Der Template-Typ (z.B. "verification", "password-reset")
     * @return Der Template-Inhalt als String
     */
    public String loadTemplate(String locale, String branding, String templateType) {
        String path = String.format("email-templates/%s/%s/%s/email.html", locale, branding, templateType);

        try {
            ClassPathResource resource = new ClassPathResource(path);

            // Fallback 1: Versuche Standard-Branding mit angeforderter Locale
            if (!resource.exists()) {
                logger.warn("Template not found: {}. Trying default branding: {}", path, defaultBranding);
                path = String.format("email-templates/%s/%s/%s/email.html", locale, defaultBranding, templateType);
                resource = new ClassPathResource(path);
            }

            // Fallback 2: Versuche Standard-Locale mit angefordertem Branding
            if (!resource.exists()) {
                logger.warn("Template not found: {}. Trying default locale: {}", path, defaultLocale);
                path = String.format("email-templates/%s/%s/%s/email.html", defaultLocale, branding, templateType);
                resource = new ClassPathResource(path);
            }

            // Fallback 3: Versuche Standard-Locale mit Standard-Branding
            if (!resource.exists()) {
                logger.warn("Template not found: {}. Falling back to default locale and branding", path);
                path = String.format("email-templates/%s/%s/%s/email.html", defaultLocale, defaultBranding, templateType);
                resource = new ClassPathResource(path);
            }

            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load email template: {}", path, e);
            throw new RuntimeException("Failed to load email template: " + path, e);
        }
    }

    /**
     * Lädt das Verifizierungs-E-Mail-Template.
     *
     * @param locale   Die Locale
     * @param branding Das Branding
     * @return Der Template-Inhalt
     */
    public String loadVerificationTemplate(String locale, String branding) {
        return loadTemplate(locale, branding, VERIFICATION_TEMPLATE);
    }

    /**
     * Lädt das Verifizierungs-E-Mail-Template mit Standard-Branding.
     *
     * @param locale Die Locale
     * @return Der Template-Inhalt
     */
    public String loadVerificationTemplate(String locale) {
        return loadVerificationTemplate(locale, defaultBranding);
    }

    /**
     * Lädt das Verifizierungs-E-Mail-Template mit Standard-Locale und Standard-Branding.
     *
     * @return Der Template-Inhalt
     */
    public String loadVerificationTemplate() {
        return loadVerificationTemplate(defaultLocale, defaultBranding);
    }

    /**
     * Lädt das Password-Reset-E-Mail-Template.
     *
     * @param locale   Die Locale
     * @param branding Das Branding
     * @return Der Template-Inhalt
     */
    public String loadPasswordResetTemplate(String locale, String branding) {
        return loadTemplate(locale, branding, PASSWORD_RESET_TEMPLATE);
    }

    /**
     * Lädt das Password-Reset-E-Mail-Template mit Standard-Branding.
     *
     * @param locale Die Locale
     * @return Der Template-Inhalt
     */
    public String loadPasswordResetTemplate(String locale) {
        return loadPasswordResetTemplate(locale, defaultBranding);
    }

    /**
     * Lädt das Password-Reset-E-Mail-Template mit Standard-Locale und Standard-Branding.
     *
     * @return Der Template-Inhalt
     */
    public String loadPasswordResetTemplate() {
        return loadPasswordResetTemplate(defaultLocale, defaultBranding);
    }

    /**
     * Ersetzt Platzhalter im Template mit tatsächlichen Werten.
     *
     * @param template     Das Template mit Platzhaltern
     * @param placeholders Map mit Platzhaltern und deren Werten
     * @return Das ausgefüllte Template
     */
    public String replacePlaceholders(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }
}
