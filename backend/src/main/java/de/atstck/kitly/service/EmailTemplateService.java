package de.atstck.kitly.service;

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
    private static final String ONBOARDING_TEMPLATE = "onboarding";
    private static final String INVOICE_TEMPLATE = "invoice";
    private static final String NEWSLETTER_CONFIRMATION_TEMPLATE = "newsletter-confirmation";

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

            String content = resource.getContentAsString(StandardCharsets.UTF_8);

            // Prüfe, ob das Template einen Base-Template-Import enthält
            return processBaseTemplate(content, branding);
        } catch (IOException e) {
            logger.error("Failed to load email template: {}", path, e);
            throw new RuntimeException("Failed to load email template: " + path, e);
        }
    }

    /**
     * Verarbeitet Base-Template-Imports im Template.
     * Sucht nach {{BASE_TEMPLATE}} und ersetzt es mit dem entsprechenden Base-Template-CSS.
     *
     * @param content  Der Template-Inhalt
     * @param branding Das Branding (kitly oder nim)
     * @return Der verarbeitete Template-Inhalt
     */
    private String processBaseTemplate(String content, String branding) {
        if (!content.contains("{{BASE_TEMPLATE}}")) {
            // Kein Base-Template-Import, gib Content unverändert zurück
            return content;
        }

        try {
            // Bestimme das Base-Template basierend auf dem Branding
            String baseTemplateName = branding.equals("nim") ? "base-nim.html" : "base-kitly.html";
            String baseTemplatePath = "email-templates/" + baseTemplateName;

            ClassPathResource baseResource = new ClassPathResource(baseTemplatePath);
            String baseContent = baseResource.getContentAsString(StandardCharsets.UTF_8);

            // Extrahiere nur den CSS-Teil aus dem Base-Template (zwischen <style> und </style>)
            int styleStart = baseContent.indexOf("<style>");
            int styleEnd = baseContent.indexOf("</style>");

            if (styleStart != -1 && styleEnd != -1) {
                String cssContent = baseContent.substring(styleStart + 7, styleEnd).trim();
                return content.replace("{{BASE_TEMPLATE}}", cssContent);
            } else {
                logger.warn("Could not extract CSS from base template: {}", baseTemplateName);
                return content.replace("{{BASE_TEMPLATE}}", "/* Base template CSS not found */");
            }
        } catch (IOException e) {
            logger.error("Failed to load base template for branding: {}", branding, e);
            return content.replace("{{BASE_TEMPLATE}}", "/* Base template loading failed */");
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
     * Lädt das Onboarding-E-Mail-Template.
     *
     * @param locale   Die Locale
     * @param branding Das Branding
     * @return Der Template-Inhalt
     */
    public String loadOnboardingTemplate(String locale, String branding) {
        return loadTemplate(locale, branding, ONBOARDING_TEMPLATE);
    }

    /**
     * Lädt das Rechnungs-E-Mail-Template.
     *
     * @param locale   Die Locale
     * @param branding Das Branding
     * @return Der Template-Inhalt
     */
    public String loadInvoiceTemplate(String locale, String branding) {
        return loadTemplate(locale, branding, INVOICE_TEMPLATE);
    }

    /**
     * Lädt das Newsletter-Confirmation-E-Mail-Template.
     *
     * @param locale   Die Locale
     * @param branding Das Branding
     * @return Der Template-Inhalt
     */
    public String loadNewsletterConfirmationTemplate(String locale, String branding) {
        return loadTemplate(locale, branding, NEWSLETTER_CONFIRMATION_TEMPLATE);
    }

    /**
     * Lädt das Newsletter-Confirmation-E-Mail-Template mit Standard-Branding.
     *
     * @param locale Die Locale
     * @return Der Template-Inhalt
     */
    public String loadNewsletterConfirmationTemplate(String locale) {
        return loadNewsletterConfirmationTemplate(locale, defaultBranding);
    }

    /**
     * Lädt das Newsletter-Confirmation-E-Mail-Template mit Standard-Locale und Standard-Branding.
     *
     * @return Der Template-Inhalt
     */
    public String loadNewsletterConfirmationTemplate() {
        return loadNewsletterConfirmationTemplate(defaultLocale, defaultBranding);
    }

    /**
     * Lädt das Onboarding-E-Mail-Template mit Standard-Branding.
     *
     * @param locale Die Locale
     * @return Der Template-Inhalt
     */
    public String loadOnboardingTemplate(String locale) {
        return loadOnboardingTemplate(locale, defaultBranding);
    }

    /**
     * Lädt das Onboarding-E-Mail-Template mit Standard-Locale und Standard-Branding.
     *
     * @return Der Template-Inhalt
     */
    public String loadOnboardingTemplate() {
        return loadOnboardingTemplate(defaultLocale, defaultBranding);
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
