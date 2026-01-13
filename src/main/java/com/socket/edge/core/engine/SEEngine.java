package com.socket.edge.core.engine;

import com.socket.edge.core.ChannelCfgSelector;
import com.socket.edge.core.iso.Iso8583ProfileResolver;
import com.socket.edge.core.LoadAware;
import com.socket.edge.core.cache.CorrelationStore;
import com.socket.edge.core.MessageContext;
import com.socket.edge.core.transport.Transport;
import com.socket.edge.core.transport.TransportProvider;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.Metadata;
import com.socket.edge.model.Direction;
import com.socket.edge.model.Iso8583Profile;
import com.socket.edge.utils.ConfigUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SEEngine extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(SEEngine.class);

    private Metadata metadata;
    private Iso8583ProfileResolver profileProcessor;
    private ChannelCfgSelector channelCfgSelector;
    private CorrelationStore correlationStore;
    private TransportProvider transportProvider;
    private ConfigUtil cu = new ConfigUtil();

    public SEEngine(Metadata metadata, Iso8583ProfileResolver profileProcessor,
                    ChannelCfgSelector channelCfgSelector, CorrelationStore correlationStore,
                    TransportProvider transportProvider) {
        this.metadata = metadata;
        this.profileProcessor = profileProcessor;
        this.channelCfgSelector = channelCfgSelector;
        this.correlationStore = correlationStore;
        this.transportProvider = transportProvider;
    }

    @Override
    public void configure() throws Exception {
        onException(Exception.class)
                .handled(true)
                .process(e -> {
                    Exception ex = e.getProperty(
                            Exchange.EXCEPTION_CAUGHT,
                            Exception.class
                    );

                    MessageContext ctx =
                            e.getIn().getBody(MessageContext.class);

                    // logging aman
                    if (ctx != null) {
                        log.error(
                                "[ERROR] corrKey=" + ctx.getCorrelationKey()
                                        + " msg=" + ex.getMessage()
                        );
                    } else {
                        log.error(
                                "[ERROR] msg=" + ex.getMessage()
                        );
                    }
                });

        from("seda:receive?concurrentConsumers=" + cu.getInt("engine.seda.receive.consumers",8)
                        + "&blockWhenFull=" + cu.getBoolean("engine.seda.receive.block-when-full", false)
                        + "&size=" + cu.getInt("engine.seda.receive.queue-size", 1000))
                .routeId("engine-receive")

                // 1. resolve channel config by socket
                .process(exchange -> {
                    MessageContext ctx = exchange.getIn().getBody(MessageContext.class);

                    ChannelCfg cfg = channelCfgSelector.select(
                            ctx.getChannelName(),
                            ctx.getInboundSocketType(),
                            ctx.getLocalAddress(),
                            ctx.getRemoteAddress(),
                            metadata.channelCfgs()
                    );

                    ctx.setChannelCfg(cfg);
                })

                // 2. resolve profile & direction
                .process(exchange -> {
                    MessageContext ctx = exchange.getIn().getBody(MessageContext.class);

                    Iso8583Profile profile =
                            metadata.profiles().get(ctx.getChannelCfg().profile());

                    if (ctx.field(cu.getString("message.packager.key")) == null) {
                        throw new IllegalArgumentException("Missing MTI (de1)");
                    }

                    for (String de : profile.correlationFields()) {
                        if (ctx.field(de) == null) {
                            throw new IllegalArgumentException(
                                    "Missing correlation field: " + de);
                        }
                    }

                    Direction dir =
                            profileProcessor.resolveDirection(ctx, profile);

                    ctx.setProfile(profile);
                    ctx.setDirection(dir);
                })

                // 3) build correlation key (once)
                .process(e -> {
                    MessageContext ctx = e.getIn().getBody(MessageContext.class);

                    Iso8583Profile profile =
                            metadata.profiles().get(ctx.getChannelCfg().profile());

                    String key =
                            profileProcessor.buildCorrelationKey(ctx, profile);

                    ctx.setCorrelationKey(key);
                })

                // 3. route by direction
                .choice()
                    .when(simple("${body.direction} == 'INBOUND'"))
                        .to("seda:inbound")
                    .when(simple("${body.direction} == 'OUTBOUND'"))
                        .to("seda:outbound")
                    .otherwise()
                        .to("seda:unknown");

        from("seda:inbound?concurrentConsumers=" + cu.getInt("engine.seda.inbound.consumers", 8)
                + "&blockWhenFull=" + cu.getBoolean("engine.seda.inbound.block-when-full", false)
                + "&size=" + cu.getInt("engine.seda.inbound.queue-size", 1000))
                .routeId("engine-inbound")
                .process(e -> {
                    MessageContext ctx = e.getIn().getBody(MessageContext.class);

                    correlationStore.put(
                            ctx.getCorrelationKey(),
                            ctx.getChannel()
                    );
                })
                .process(e -> {
                    MessageContext ctx = e.getIn().getBody(MessageContext.class);

                    Transport transport =
                            transportProvider.resolve(ctx.getChannelCfg(), ctx.getOutboundSocketType());

                    if (!transport.isUp()) {
                        throw new IllegalStateException("Transport DOWN");
                    }

                    transport.send(ctx);
                    log.info("took time {}ms", (System.currentTimeMillis()-(long)ctx.getProperty("received_time")));
                });

        from("seda:outbound?concurrentConsumers=" + cu.getInt("engine.seda.outbound.consumers", 8)
                + "&blockWhenFull=" + cu.getBoolean("engine.seda.outbound.block-when-full", false)
                + "&size=" + cu.getInt("engine.seda.outbound.queue-size", 1000))
                .routeId("engine-outbound")

                .process(exchange -> {
                    MessageContext ctx = exchange.getIn().getBody(MessageContext.class);
                    try {
                        Channel inbound =
                                correlationStore.get(ctx.getCorrelationKey());

                        if (inbound == null || !inbound.isActive()) {
                            throw new IllegalStateException(
                                    "No inbound channel for correlation="
                                            + ctx.getCorrelationKey()
                            );
                        }

                        inbound.writeAndFlush(Unpooled.wrappedBuffer(ctx.getRawBytes()));
                        log.info("Send response " + new String(ctx.getRawBytes()));
                        log.info("took time {}ms", (System.currentTimeMillis()-(long)ctx.getProperty("received_time")));
                    } finally {
                        correlationStore.remove(ctx.getCorrelationKey());
                        LoadAware loadAware = (LoadAware) ctx.getProperty("back_forward_channel");
                        if (loadAware != null) {
                            loadAware.decrement();
                        }
                    }
                });

        from("seda:unknown")
                .routeId("engine-unknown")
                .process(e -> {
                    MessageContext ctx = e.getIn().getBody(MessageContext.class);
                    log.warn("no channel found for " + new String(ctx.getRawBytes()));
                });
    }
}
