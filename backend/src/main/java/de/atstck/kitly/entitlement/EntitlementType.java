package de.atstck.kitly.entitlement;

/**
 * Type of an entitlement.
 * Used to categorize entitlements into features, app access, and limits.
 */
public enum EntitlementType {
    /**
     * Feature flags (e.g., ai_assistant)
     * Stored as: "features.{name}"
     */
    FEATURE("features"),

    /**
     * App access permissions (e.g., nim)
     * Stored as: "app_access.{name}"
     */
    APP_ACCESS("app_access"),

    /**
     * Limits and quotas (e.g., projects, api_calls_per_month)
     * Stored as: "limits.{name}"
     */
    LIMIT("limits");

    private final String prefix;

    EntitlementType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Build the full entitlement key from type and name
     * @param name The entitlement name (e.g., "ai_assistant", "nim", "projects")
     * @return The full key (e.g., "features.ai_assistant", "app_access.nim", "limits.projects")
     */
    public String buildKey(String name) {
        return prefix + "." + name;
    }

    /**
     * Parse an entitlement type from a key
     * @param key The full key (e.g., "features.ai_assistant")
     * @return The entitlement type, or null if the key doesn't match any type
     */
    public static EntitlementType fromKey(String key) {
        if (key == null || !key.contains(".")) {
            return null;
        }
        String prefix = key.substring(0, key.indexOf('.'));
        for (EntitlementType type : values()) {
            if (type.prefix.equals(prefix)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Extract the name from a full key
     * @param key The full key (e.g., "features.ai_assistant")
     * @return The name part (e.g., "ai_assistant"), or null if invalid
     */
    public static String extractName(String key) {
        if (key == null || !key.contains(".")) {
            return null;
        }
        return key.substring(key.indexOf('.') + 1);
    }
}

