package com.socket.edge.core;

import com.socket.edge.constant.SocketType;
import com.socket.edge.model.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChannelCfgSelectorTest {

    private ChannelCfgSelector selector;

    @BeforeEach
    void setUp() {
        selector = new ChannelCfgSelector();
    }

    // ================= CLIENT =================

    @Test
    void select_client_shouldMatchByHostAndPort_only() {
        ChannelCfg cfg = clientCfg(
                "payment",
                "OUTBOUND",
                "PROFILE_A",
                "ROUND_ROBIN",
                endpoint("10.10.10.1", 9000, 5, 1)
        );

        ChannelCfg result = selector.select(
                "payment",
                SocketType.CLIENT,
                local(50000),
                remote("10.10.10.1", 9000),
                List.of(cfg)
        );

        assertSame(cfg, result);
    }

    @Test
    void select_client_shouldIgnoreTypeAndProfile() {
        ChannelCfg cfg = clientCfg(
                "payment",
                "SOME_RANDOM_TYPE",
                "SOME_PROFILE",
                "WEIGHTED",
                endpoint("10.10.10.1", 9000, 10, 9)
        );

        ChannelCfg result = selector.select(
                "payment",
                SocketType.CLIENT,
                local(55000),
                remote("10.10.10.1", 9000),
                List.of(cfg)
        );

        assertSame(
                cfg,
                result,
                "Selector must not depend on ChannelCfg.type or profile"
        );
    }

    // ================= SERVER =================

    @Test
    void select_server_shouldMatchByListenPortAndRemoteHost_only() {
        ChannelCfg cfg = serverCfg(
                "settlement",
                "INBOUND",
                "PROFILE_X",
                "0.0.0.0",
                8080,
                "ROUND_ROBIN",
                endpoint("192.168.1.10", 0, 1, 1)
        );

        ChannelCfg result = selector.select(
                "settlement",
                SocketType.SERVER,
                local(8080),
                remote("192.168.1.10", 62000),
                List.of(cfg)
        );

        assertSame(
                cfg,
                result,
                "Selector must ignore listenHost, strategy, remotePort, type, profile"
        );
    }

    @Test
    void select_server_shouldNotMatchWhenRemoteHostNotInPool() {
        ChannelCfg cfg = serverCfg(
                "settlement",
                "INBOUND",
                "PROFILE_X",
                "127.0.0.1",
                8080,
                "WEIGHTED",
                endpoint("192.168.1.10", 0, 1, 1)
        );

        assertThrows(
                IllegalStateException.class,
                () -> selector.select(
                        "settlement",
                        SocketType.SERVER,
                        local(8080),
                        remote("192.168.1.99", 62000),
                        List.of(cfg)
                )
        );
    }

    // ================= MULTI CONFIG =================

    @Test
    void select_shouldReturnFirstMatchedConfig_whenDuplicatesExist() {
        ChannelCfg first = clientCfg(
                "payment",
                "OUTBOUND",
                "PROFILE_A",
                "ROUND_ROBIN",
                endpoint("10.10.10.1", 9000, 1, 1)
        );

        ChannelCfg second = clientCfg(
                "payment",
                "OUTBOUND",
                "PROFILE_B",
                "WEIGHTED",
                endpoint("10.10.10.1", 9000, 99, 9)
        );

        ChannelCfg result = selector.select(
                "payment",
                SocketType.CLIENT,
                local(50000),
                remote("10.10.10.1", 9000),
                List.of(first, second)
        );

        assertSame(
                first,
                result,
                "Selector must be deterministic: first match wins"
        );
    }

    // ================= TEST DSL =================

    private static ChannelCfg clientCfg(
            String name,
            String type,
            String profile,
            String strategy,
            SocketEndpoint... endpoints
    ) {
        return new ChannelCfg(
                name,
                type,
                null,
                new ClientChannel(List.of(endpoints), strategy),
                profile
        );
    }

    private static ChannelCfg serverCfg(
            String name,
            String type,
            String profile,
            String listenHost,
            int listenPort,
            String strategy,
            SocketEndpoint... pool
    ) {
        return new ChannelCfg(
                name,
                type,
                new ServerChannel(
                        listenHost,
                        listenPort,
                        List.of(pool),
                        strategy
                ),
                null,
                profile
        );
    }

    private static SocketEndpoint endpoint(
            String host,
            int port,
            int weight,
            int priority
    ) {
        return new SocketEndpoint(host, port, weight, priority);
    }

    private static InetSocketAddress local(int port) {
        return new InetSocketAddress("0.0.0.0", port);
    }

    private static InetSocketAddress remote(String host, int port) {
        return new InetSocketAddress(host, port);
    }
}
