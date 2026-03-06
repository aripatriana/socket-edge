package com.socket.edge.core.engine;

import com.socket.edge.constant.Direction;
import com.socket.edge.core.ChannelCfgSelector;
import com.socket.edge.core.MetadataHolder;
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
import com.socket.edge.utils.ConfigUtil;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * Core routing engine for ISO 8583 message processing.
 *
 * <p>{@code SEEngine} is an Apache Camel {@link RouteBuilder} that orchestrates
 * end-to-end ISO 8583 message handling, including:
 * <ul>
 *   <li>Inbound message reception</li>
 *   <li>Channel configuration resolution</li>
 *   <li>ISO profile and direction resolution</li>
 *   <li>Correlation key generation</li>
 *   <li>Inbound / outbound routing</li>
 *   <li>Request–response correlation management</li>
 *   <li>Transport dispatch and socket reply handling</li>
 * </ul>
 *
 * <p>The engine is designed to be:
 * <ul>
 *   <li>Asynchronous (SEDA-based)</li>
 *   <li>Cluster-friendly</li>
 *   <li>Profile-driven</li>
 *   <li>Transport-agnostic</li>
 * </ul>
 *
 * <p>Message flow (high level):
 * <ol>
 *   <li>Receive message from socket layer</li>
 *   <li>Resolve channel configuration</li>
 *   <li>Resolve ISO profile and direction</li>
 *   <li>Build correlation key</li>
 *   <li>Route to inbound or outbound flow</li>
 *   <li>Dispatch via transport or reply via socket</li>
 * </ol>
 *
 * <p>This class is instantiated once per Camel context.</p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class SEEngine extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(SEEngine.class);

    /**
     * Socket manager used to resolve sockets for outbound replies.
     */
    private SocketManager socketManager;

    /**
     * Holder for runtime metadata (channels, profiles, etc).
     */
    private final MetadataHolder metadataHolder;

    /**
     * ISO 8583 profile resolver.
     */
    private final Iso8583ProfileResolver profileProcessor;

    /**
     * Channel configuration selector.
     */
    private final ChannelCfgSelector channelCfgSelector;

    /**
     * Correlation store for request–response mapping.
     */
    private CorrelationStore correlationStore;

    /**
     * Transport provider for outbound message delivery.
     */
    private final TransportProvider transportProvider;

    /**
     * Configuration utility.
     */
    private final ConfigUtil cu = new ConfigUtil();

    /**
     * Creates a new SEEngine instance.
     *
     * @param metadataHolder      runtime metadata holder
     * @param profileProcessor   ISO profile resolver
     * @param channelCfgSelector channel configuration selector
     * @param correlationStore   correlation store
     * @param transportProvider  transport provider
     */
    public SEEngine(
            MetadataHolder metadataHolder,
            Iso8583ProfileResolver profileProcessor,
            ChannelCfgSelector channelCfgSelector,
            CorrelationStore correlationStore,
            TransportProvider transportProvider
    ) {
        this.metadataHolder = metadataHolder;
        this.profileProcessor = profileProcessor;
        this.channelCfgSelector = channelCfgSelector;
        this.correlationStore = correlationStore;
        this.transportProvider = transportProvider;
    }

    /**
     * Binds the socket manager after initialization.
     *
     * <p>This is required for resolving reply sockets
     * during outbound message handling.</p>
     *
     * @param socketManager socket manager
     */
    public void bindSocketManager(SocketManager socketManager) {
        this.socketManager = socketManager;
    }

    /**
     * Binds the {@link CorrelationStore} after component initialization.
     *
     * <p>This binding is required to resolve reply sockets during
     * outbound message handling.</p>
     *
     * @param correlationStore the correlation store instance to bind
     */
    public void bindCorrelationStore(CorrelationStore correlationStore) {
        this.correlationStore = correlationStore;
    }

    /**
     * Configures all Camel routes for the engine.
     *
     * <p>This method defines:
     * <ul>
     *   <li>Global exception handling</li>
     *   <li>Message receive route</li>
     *   <li>Inbound processing route</li>
     *   <li>Outbound processing route</li>
     *   <li>Fallback route for unknown direction</li>
     * </ul>
     */
    @Override
    public void configure() throws Exception {

        /*
         * Global exception handler
         */
        onException(Exception.class)
                .handled(true)
                .process(e -> {
                    Exception ex = e.getProperty(
                            Exchange.EXCEPTION_CAUGHT,
                            Exception.class
                    );

                    MessageContext ctx = e.getIn().getBody(MessageContext.class);

                    if (ctx != null) {
                        ctx.getSocketChannel().onError();
                        log.error(
                                "corrKey={} errMsg={} msg={}",
                                ctx.getCorrelationKey(),
                                ex.getMessage(),
                                new String(ctx.getRawBytes())
                        );
                    } else {
                        log.error("errMsg={}", ex.getMessage());
                    }
                });

        /*
         * Receive route
         */
        from("seda:receive?concurrentConsumers=" + cu.getInt("engine.seda.receive.consumers", 8)
                + "&blockWhenFull=" + cu.getBoolean("engine.seda.receive.block-when-full", false)
                + "&size=" + cu.getInt("engine.seda.receive.queue-size", 1000))
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

                    Iso8583Profile profile = metadataHolder.get().profiles().get(ctx.getChannelCfg().profile());

                    if (ctx.field(cu.getString("message.packager.key")) == null) {
                        throw new IllegalArgumentException("Missing MTI (de1)");
                    }

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

                    Iso8583Profile profile =
                            metadataHolder.get().profiles()
                                    .get(ctx.getChannelCfg().profile());

                    String key = profileProcessor.buildCorrelationKey(ctx, profile);

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
        from("seda:inbound?concurrentConsumers=" + cu.getInt("engine.seda.inbound.consumers", 8)
                + "&blockWhenFull=" + cu.getBoolean("engine.seda.inbound.block-when-full", false)
                + "&size=" + cu.getInt("engine.seda.inbound.queue-size", 1000))
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

                    Transport transport =
                            transportProvider.resolve(
                                    ctx.getChannelCfg(),
                                    ctx.getOutboundType()
                            );

                    if (!transport.isActive()) {
                        throw new IllegalStateException("Transport NOT ACTIVE");
                    }

                    transport.send(ctx);

                    long latencyNs = System.nanoTime() - (long) ctx.getProperty("receivedTimeNs");
                    ctx.getSocketChannel().onComplete(latencyNs);
                });

        /*
         * Outbound route (reply)
         */
        from("seda:outbound?concurrentConsumers=" + cu.getInt("engine.seda.outbound.consumers", 8)
                + "&blockWhenFull=" + cu.getBoolean("engine.seda.outbound.block-when-full", false)
                + "&size=" + cu.getInt("engine.seda.outbound.queue-size", 1000))
                .routeId("engine-outbound")
                .process(exchange -> {
                    MessageContext ctx = exchange.getIn().getBody(MessageContext.class);
                    try {
                        CorrelationEntry replyEntry = correlationStore.get(ctx.getCorrelationKey());

                        if (replyEntry == null) {
                            throw new IllegalStateException( "No inbound correlation entry for correlation=" + ctx.getCorrelationKey());
                        }

                        AbstractSocket replySocket = socketManager.getSocket(replyEntry.replySocketId());

                        if (replySocket == null) {
                            throw new IllegalStateException("No inbound socket for correlation=" + ctx.getCorrelationKey());
                        }

                        SocketChannel replyChannel =
                                replySocket.channelPool()
                                        .getChannelById(
                                                replyEntry.replyChannelId()
                                        );

                        if (replyChannel != null && replyChannel.isActive()) {
                            replyChannel.send(ctx.getRawBytes());
                        } else {
                            List<SocketChannel> candidates = replySocket.channelPool().activeChannels();

                            if (candidates != null && !candidates.isEmpty()) {
                                log.warn("corrKey={} original channel inactive, falling back to alternate channel",
                                        ctx.getCorrelationKey());
                                candidates.get(0).send(ctx.getRawBytes());
                            } else {
                                throw new IllegalStateException("No channel active for correlation=" + ctx.getCorrelationKey());
                            }
                        }

                        long latencyNs = System.nanoTime() - (long) ctx.getProperty("receivedTimeNs");
                        ctx.getSocketChannel().onComplete(latencyNs);

                    } finally {
                        correlationStore.remove(ctx.getCorrelationKey());
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
                    log.warn("No channel found for message={}", new String(ctx.getRawBytes()));
                });
    }
}
