package com.socket.edge.core.iso;

import com.socket.edge.core.MessageContext;
import com.socket.edge.core.SystemConfig;
import com.socket.edge.constant.Direction;
import com.socket.edge.model.Iso8583Profile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves ISO 8583 message characteristics based on profile definitions.
 *
 * <p>v3.0 changes:
 * <ul>
 *   <li>Added {@link #resolveProfile} — loops multiple profiles to find
 *       which one handles the given MTI</li>
 *   <li>Correlation key uses {@code field=value} format to prevent collision</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class Iso8583ProfileResolver {

    private final String packagerKey;

    public Iso8583ProfileResolver(SystemConfig config) {
        this.packagerKey = config.packagerKey();
    }

    /**
     * Resolves which profile handles the given MTI from a list of profile names.
     *
     * <p>Loops through the channel's assigned profiles and returns the first
     * one that contains the MTI in either inbound or outbound direction.</p>
     *
     * @param ctx          message context (must have MTI field populated)
     * @param profileNames list of profile names assigned to the channel
     * @param allProfiles  map of all available profiles
     * @return matched profile
     * @throws IllegalStateException if MTI not found in any profile
     */
    public Iso8583Profile resolveProfile(
            MessageContext ctx,
            List<String> profileNames,
            Map<String, Iso8583Profile> allProfiles
    ) {
        String mti = ctx.field(packagerKey);
        if (mti == null) {
            throw new IllegalArgumentException("Missing MTI (" + packagerKey + ")");
        }

        for (String name : profileNames) {
            Iso8583Profile profile = allProfiles.get(name);
            if (profile == null) continue;

            for (Direction dir : Direction.values()) {
                if (profile.valuesFor(dir).contains(mti)) {
                    return profile;
                }
            }
        }

        throw new IllegalStateException(
                "Unknown MTI: " + mti + ", channel=" + ctx.getChannelName()
                        + ". Add to profile or create new profile for this MTI.");
    }

    /**
     * Resolves the message direction based on MTI within a specific profile.
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
     * <p>Format: {@code channelName||de2=123456|de11=000001|de37=123456789012}</p>
     */
    public String buildCorrelationKey(MessageContext ctx, Iso8583Profile profile) {
        String channel = ctx.getChannelCfg().name();

        String key = profile.correlationFields().stream()
                .map(f -> {
                    String val = ctx.field(f);
                    if (val == null) {
                        throw new IllegalStateException(
                                "Correlation field missing: " + f + ", channel=" + channel);
                    }
                    return f + "=" + val;
                })
                .collect(Collectors.joining("|"));

        return channel + "||" + key;
    }
}
