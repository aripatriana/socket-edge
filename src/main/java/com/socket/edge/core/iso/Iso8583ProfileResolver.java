package com.socket.edge.core.iso;

import com.socket.edge.core.MessageContext;
import com.socket.edge.core.SystemConfig;
import com.socket.edge.constant.Direction;
import com.socket.edge.model.Iso8583Profile;

import java.util.stream.Collectors;

/**
 * Resolves ISO 8583 message characteristics based on a profile definition.
 *
 * <p>v3.0 changes:
 * <ul>
 *   <li>Config injected via constructor instead of static {@code SystemBootstrap.getConfig()}</li>
 *   <li>Fixed: Correlation key collision — now uses {@code fieldName=value} format
 *       to prevent ambiguity (e.g. de2=123|de11=456 vs de2=12|de11=3456)</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class Iso8583ProfileResolver {

    private final String packagerKey;

    /**
     * Creates a new resolver with injected configuration.
     *
     * @param config system configuration
     */
    public Iso8583ProfileResolver(SystemConfig config) {
        this.packagerKey = config.packagerKey();
    }

    /**
     * Resolves the message direction based on MTI.
     *
     * @param ctx     message context containing ISO fields
     * @param profile ISO 8583 profile definition
     * @return resolved message direction
     * @throws IllegalStateException if the MTI is not mapped
     */
    public Direction resolveDirection(MessageContext ctx, Iso8583Profile profile) {
        String mti = ctx.field(packagerKey);

        for (Direction direction : Direction.values()) {
            if (profile.valuesFor(direction).contains(mti)) {
                return direction;
            }
        }

        throw new IllegalStateException("Unknown MTI: " + mti);
    }

    /**
     * Builds a correlation key for matching request and response messages.
     *
     * <p>v3.0 format uses explicit field names to prevent collision:
     * <pre>
     * channelName||de2=123456|de11=000001|de37=123456789012
     * </pre>
     *
     * @param ctx     message context containing ISO fields
     * @param profile ISO 8583 profile definition
     * @return correlation key string
     * @throws IllegalStateException if any correlation field is missing
     */
    public String buildCorrelationKey(MessageContext ctx, Iso8583Profile profile) {
        String channel = ctx.getChannelCfg().name();

        String key = profile.correlationFields().stream()
                .map(f -> {
                    String val = ctx.field(f);
                    if (val == null) {
                        throw new IllegalStateException(
                                "Correlation field missing: " + f + ", channel=" + channel
                        );
                    }
                    // v3.0 FIX: include field name to prevent collision
                    return f + "=" + val;
                })
                .collect(Collectors.joining("|"));

        return channel + "||" + key;
    }
}
