package com.socket.edge.utils;

import com.socket.edge.core.SystemConfig;

import java.util.Map;
import java.util.Set;

/**
 * PCI-DSS field masking utility for ISO 8583 log sanitization.
 *
 * <p>Masks sensitive cardholder data fields before they appear in
 * any log output (application logs, audit trail, error logs).</p>
 *
 * <p>Supports two strategies:
 * <ul>
 *   <li>{@code partial} — show first 6 and last 4 digits: {@code 123456****5678}</li>
 *   <li>{@code full} — replace entire value: {@code ****}</li>
 * </ul>
 *
 * <p>Thread-safe and stateless after construction.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class PciMaskUtil {

    private final boolean enabled;
    private final Set<String> maskFields;
    private final String strategy;

    public PciMaskUtil(SystemConfig.PciConfig pciConfig) {
        this.enabled = pciConfig.enabled();
        this.maskFields = Set.copyOf(pciConfig.maskFields());
        this.strategy = pciConfig.maskStrategy();
    }

    /**
     * Creates a disabled (no-op) masking utility.
     */
    public static PciMaskUtil disabled() {
        return new PciMaskUtil(new SystemConfig.PciConfig(false, java.util.List.of(), "full"));
    }

    /**
     * Checks whether the given field name is a PCI-sensitive field.
     */
    public boolean isSensitive(String fieldName) {
        return enabled && maskFields.contains(fieldName);
    }

    /**
     * Masks a single field value according to the configured strategy.
     *
     * @param fieldName ISO field name (e.g. "de2")
     * @param value     raw field value
     * @return masked value if field is sensitive, original value otherwise
     */
    public String mask(String fieldName, String value) {
        if (!enabled || value == null || !maskFields.contains(fieldName)) {
            return value;
        }

        return switch (strategy) {
            case "partial" -> maskPartial(value);
            case "full" -> "****";
            default -> "****";
        };
    }

    /**
     * Returns a masked copy of ISO field map for safe logging.
     *
     * @param fields original ISO fields
     * @return new map with sensitive fields masked
     */
    public Map<String, String> maskAll(Map<String, String> fields) {
        if (!enabled || fields == null) {
            return fields;
        }

        var masked = new java.util.LinkedHashMap<>(fields);
        for (String key : maskFields) {
            if (masked.containsKey(key)) {
                masked.put(key, mask(key, masked.get(key)));
            }
        }
        return masked;
    }

    /**
     * Formats ISO fields as a safe log string.
     * Example: "MTI=0200 de2=123456****5678 de11=000001 de37=123456789012"
     *
     * @param fields  ISO field map
     * @param include field names to include (null = all)
     * @return formatted safe log string
     */
    public String safeLogString(Map<String, String> fields, String... include) {
        if (fields == null) return "";

        var sb = new StringBuilder();
        var keys = (include != null && include.length > 0)
                ? java.util.Arrays.asList(include)
                : fields.keySet();

        for (String key : keys) {
            String value = fields.get(key);
            if (value == null) continue;

            if (sb.length() > 0) sb.append(' ');
            sb.append(key).append('=').append(mask(key, value));
        }
        return sb.toString();
    }

    /**
     * Partial masking: show first 6 and last 4 characters.
     * If value is too short (≤10 chars), show first 2 and last 2.
     * If value is ≤4 chars, full mask.
     */
    private static String maskPartial(String value) {
        int len = value.length();
        if (len <= 4) {
            return "****";
        }
        if (len <= 10) {
            return value.substring(0, 2)
                    + "*".repeat(len - 4)
                    + value.substring(len - 2);
        }
        return value.substring(0, 6)
                + "*".repeat(len - 10)
                + value.substring(len - 4);
    }
}
