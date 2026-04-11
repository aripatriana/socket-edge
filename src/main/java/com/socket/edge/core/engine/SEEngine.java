package com.socket.edge.core.engine;

import com.socket.edge.constant.Direction;
import com.socket.edge.core.AuditLogger;
import com.socket.edge.core.ChannelCfgSelector;
import com.socket.edge.core.MetadataHolder;
import com.socket.edge.core.SystemConfig;
import com.socket.edge.core.iso.Iso8583ProfileResolver;
import com.socket.edge.core.LoadAware;
import com.socket.edge.core.cache.CorrelationStore;
import com.socket.edge.core.MessageContext;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.socket.SocketManager;
import com.socket.edge.core.transport.Transport;
import com.socket.edge.core.transport.TransportProvider;
import com.socket.edge.model.*;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Core routing engine for ISO 8583 message processing.
 *
 * <p>v3.0 changes:
 * <ul>
 *   <li>Uses {@link SystemConfig} instead of {@code ConfigUtil} (no more static state)</li>
 *   <li>Fixed: inflight counter properly decremented in error/finally paths</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class SEEngine extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(SEEngine.class);

    private SocketManager socketManager;
    private final MetadataHolder metadataHolder;
    private final Iso8583ProfileResolver profileProcessor;
    private final ChannelCfgSelector channelCfgSelector;
    private CorrelationStore correlationStore;
    private final TransportProvider transportProvider;
    private final SystemConfig config;
    private final AuditLogger auditLogger;

    public SEEngine(
            SystemConfig config,
            MetadataHolder metadataHolder,
            Iso8583ProfileResolver profileProcessor,
            ChannelCfgSelector channelCfgSelector,
            CorrelationStore correlationStore,
            TransportProvider transportProvider,
            AuditLogger auditLogger
    ) {
        this.config = config;
        this.metadataHolder = metadataHolder;
        this.profileProcessor = profileProcessor;
        this.channelCfgSelector = channelCfgSelector;
        this.correlationStore = correlationStore;
        this.transportProvider = transportProvider;
        this.auditLogger = auditLogger;
    }

    public void bindSocketManager(SocketManager socketManager) {
        this.socketManager = socketManager;
    }

    public void bindCorrelationStore(CorrelationStore correlationStore) {
        this.correlationStore = correlationStore;
    }

    @Override
    public void configure() throws Exception {

        SystemConfig.SedaConfig seda = config.seda();

        /*
         * Global exception handler
         */
        onException(Exception.class)
                .handled(true)
                .process(e -> {
                    Exception ex = e.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    MessageContext ctx = e.getIn().getBody(MessageContext.class);

                    if (ctx != null) {
                        ctx.getSocketChannel().onError();
                        log.error("corrKey={} errMsg={}",
                                ctx.getCorrelationKey(),
                                ex.getMessage());
                    } else {
                        log.error("errMsg={}", ex.getMessage());
                    }
                });

        /*
         * Receive route
         */
        from(sedaUri("receive", seda.receive()))
                .routeId("engine-receive")

                // 1. Resolve channel configuration
                .process(exchange -> {
                    MessageContext ctx = exchange.getIn().getBody(MessageContext.class);
                    ChannelCfg cfg = channelCfgSelector.select(
                            ctx.getChannelName(),
                            ctx.getInboundType(),
                            ctx.getLocalAddress(),
                            ctx.getRemoteAddress(),
                            metadataHolder.get().channelCfgs()
                    );
                    ctx.setChannelCfg(cfg);
                })

                // 2. Resolve ISO profile and direction
                .process(exchange -> {
                    MessageContext ctx = exchange.getIn().getBody(MessageContext.class);

                    // v3.0: resolve profile from multiple profiles assigned to channel
                    Iso8583Profile profile = profileProcessor.resolveProfile(
                            ctx,
                            ctx.getChannelCfg().profiles(),
                            metadataHolder.get().profiles()
                    );

                    for (String de : profile.correlationFields()) {
                        if (ctx.field(de) == null) {
                            throw new IllegalArgumentException("Missing correlation field: " + de);
                        }
                    }

                    Direction dir = profileProcessor.resolveDirection(ctx, profile);
                    ctx.setProfile(profile);
                    ctx.setDirection(dir);
                })

                // 3. Build correlation key
                .process(e -> {
                    MessageContext ctx = e.getIn().getBody(MessageContext.class);
                    // v3.0: profile already resolved and set in step 2
                    String key = profileProcessor.buildCorrelationKey(ctx, ctx.getProfile());
                    ctx.setCorrelationKey(key);
                })

                // 4. Route by direction
                .choice()
                    .when(simple("${body.direction} == 'INBOUND'"))
                        .to("seda:inbound")
                    .when(simple("${body.direction} == 'OUTBOUND'"))
                        .to("seda:outbound")
                    .otherwise()
                        .to("seda:unknown");

        /*
         * Inbound route
         */
        from(sedaUri("inbound", seda.inbound()))
                .routeId("engine-inbound")
                .process(e -> {
                    MessageContext ctx = e.getIn().getBody(MessageContext.class);
                    correlationStore.put(
                            ctx.getCorrelationKey(),
                            CorrelationEntry.newEntry(
                                    ctx.getCorrelationKey(),
                                    ctx.getSocketId(),
                                    ctx.getSocketChannel().channelId().asLongText()
                            )
                    );
                })
                .process(e -> {
                    MessageContext ctx = e.getIn().getBody(MessageContext.class);

                    Transport transport = transportProvider.resolve(
                            ctx.getChannelCfg(),
                            ctx.getOutboundType()
                    );

                    if (!transport.isActive()) {
                        throw new IllegalStateException("Transport NOT ACTIVE");
                    }

                    transport.send(ctx);

                    long latencyNs = System.nanoTime() - (long) ctx.getProperty("receivedTimeNs");
                    ctx.getSocketChannel().onComplete(latencyNs);

                    // Audit trail
                    auditLogger.log(ctx);
                });

        /*
         * Outbound route (reply)
         */
        from(sedaUri("outbound", seda.outbound()))
                .routeId("engine-outbound")
                .process(exchange -> {
                    MessageContext ctx = exchange.getIn().getBody(MessageContext.class);
                    try {
                        CorrelationEntry replyEntry = correlationStore.get(ctx.getCorrelationKey());

                        if (replyEntry == null) {
                            throw new IllegalStateException(
                                    "No inbound correlation entry for correlation=" + ctx.getCorrelationKey());
                        }

                        AbstractSocket replySocket = socketManager.getSocket(replyEntry.replySocketId());

                        if (replySocket == null) {
                            throw new IllegalStateException(
                                    "No inbound socket for correlation=" + ctx.getCorrelationKey());
                        }

                        SocketChannel replyChannel = replySocket.channelPool()
                                .getChannelById(replyEntry.replyChannelId());

                        if (replyChannel != null && replyChannel.isActive()) {
                            replyChannel.send(ctx.getRawBytes());
                        } else {
                            List<SocketChannel> candidates = replySocket.channelPool().activeChannels();

                            if (candidates != null && !candidates.isEmpty()) {
                                log.warn("corrKey={} original channel inactive, falling back",
                                        ctx.getCorrelationKey());
                                candidates.get(0).send(ctx.getRawBytes());
                            } else {
                                throw new IllegalStateException(
                                        "No channel active for correlation=" + ctx.getCorrelationKey());
                            }
                        }

                        long latencyNs = System.nanoTime() - (long) ctx.getProperty("receivedTimeNs");
                        ctx.getSocketChannel().onComplete(latencyNs);

                        // Audit trail
                        auditLogger.log(ctx);

                    } finally {
                        correlationStore.remove(ctx.getCorrelationKey());
                        // v3.0 FIX: always decrement inflight counter
                        LoadAware la = (LoadAware) ctx.getProperty("back_forward_channel");
                        if (la != null) {
                            la.decrement();
                        }
                    }
                });

        /*
         * Unknown direction route
         */
        from("seda:unknown")
                .routeId("engine-unknown")
                .process(e -> {
                    MessageContext ctx = e.getIn().getBody(MessageContext.class);
                    log.warn("Unknown direction for message, corrKey={}", ctx.getCorrelationKey());
                });
    }

    private String sedaUri(String name, SystemConfig.StageConfig stage) {
        return "seda:" + name
                + "?concurrentConsumers=" + stage.consumers()
                + "&blockWhenFull=" + stage.blockWhenFull()
                + "&size=" + stage.queueSize();
    }
}
