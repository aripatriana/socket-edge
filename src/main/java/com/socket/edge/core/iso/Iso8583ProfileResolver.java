package com.socket.edge.core.iso;

import com.socket.edge.core.MessageContext;
import com.socket.edge.constant.Direction;
import com.socket.edge.model.Iso8583Profile;

import com.socket.edge.SystemBootstrap;

import java.util.stream.Collectors;

/**
 * Resolves ISO 8583 message characteristics based on a profile definition.
 *
 * <p>
 * {@code Iso8583ProfileResolver} is responsible for:
 * <ul>
 * <li>Determining message {@link Direction} from MTI</li>
 * <li>Building correlation keys for request–response matching</li>
 * </ul>
 *
 * <p>
 * The resolution logic is driven entirely by {@link Iso8583Profile},
 * allowing different ISO profiles (host, switch, channel-specific)
 * to be plugged in without changing code.
 * </p>
 *
 * <p>
 * This class is stateless and thread-safe.
 * </p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public final class Iso8583ProfileResolver {

    /**
     * Resolves the message direction based on MTI.
     *
     * <p>
     * The MTI is extracted from the {@link MessageContext} using
     * the configured packager key, then matched against the
     * direction mapping defined in {@link Iso8583Profile}.
     * </p>
     *
     * @param ctx     message context containing ISO fields
     * @param profile ISO 8583 profile definition
     * @return resolved message direction
     * @throws IllegalStateException if the MTI is not mapped
     *                               to any known direction
     */
    public Direction resolveDirection(
            MessageContext ctx,
            Iso8583Profile profile) {

        String mti = ctx.field(SystemBootstrap.getConfig().getString("message.packager.key"));

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
     * <p>
     * The correlation key is composed of:
     * <ul>
     * <li>Channel name</li>
     * <li>One or more ISO fields defined in the profile</li>
     * </ul>
     *
     * <p>
     * Example format:
     * 
     * <pre>
     * channelName | field11 | field37 | field41
     * </pre>
     *
     * <p>
     * All correlation fields must be present in the message context.
     * Missing fields will result in an exception.
     * </p>
     *
     * @param ctx     message context containing ISO fields
     * @param profile ISO 8583 profile definition
     * @return correlation key string
     * @throws IllegalStateException if any correlation field is missing
     */
    public String buildCorrelationKey(
            MessageContext ctx,
            Iso8583Profile profile) {
        String channel = ctx.getChannelCfg().name();

        String key = profile.correlationFields().stream()
                .map(f -> {
                    String val = ctx.field(f);
                    if (val == null) {
                        throw new IllegalStateException(
                                "Correlation field missing: " + f + ", channel=" + channel);
                    }
                    return val;
                })
                .collect(Collectors.joining("|"));

        return channel + "|" + key;
    }
}
