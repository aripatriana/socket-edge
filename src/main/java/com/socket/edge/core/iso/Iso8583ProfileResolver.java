package com.socket.edge.core.iso;

import com.socket.edge.core.MessageContext;
import com.socket.edge.model.Direction;
import com.socket.edge.model.Iso8583Profile;

import static com.socket.edge.SystemBootstrap.sc;

import java.util.stream.Collectors;

public final class Iso8583ProfileResolver {

    public Direction resolveDirection(
            MessageContext ctx,
            Iso8583Profile profile
    ) {

        String mti = ctx.field(sc.getString("message.packager.key"));

        for (Direction direction : Direction.values()) {
            if (profile.valuesFor(direction).contains(mti)) {
                return direction;
            }
        }

        throw new IllegalStateException("Unknown MTI: " + mti);
    }

    public String buildCorrelationKey(
            MessageContext ctx,
            Iso8583Profile profile
    ) {
        return ctx.getChannelCfg().name() + "|" +profile.correlationFields().stream()
                .map(ctx::field)
                .collect(Collectors.joining("|"));
    }
}
