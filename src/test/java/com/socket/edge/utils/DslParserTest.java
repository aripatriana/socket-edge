package com.socket.edge.utils;

import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.Metadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DslParserTest {

    private final DslParser parser = new DslParser();

    // ===============================
    // 1️⃣ HAPPY PATH
    // ===============================
    @Test
    void shouldParseValidDsl() {

        String dsl = """
            channel {
                name fello
                type tcp

                server {
                    listen 127.0.0.1 26000
                    pool ip_fello1
                    pool ip_fello2
                }

                client {
                    connect ip_apps1 26000 weight 100 priority 1
                    connect ip_apps2 26000 weight 100 priority 0
                    strategy roundrobin
                }

                profile iso8583
            }

            profile iso8583 {
                direction inbound {
                    de1 in ["0200", "0800"]
                }

                correlation {
                    de2
                    de11
                }
            }
            """;

        Metadata md = parser.parse(dsl);

        assertEquals(1, md.channelCfgs().size());
        ChannelCfg ch = md.channelCfgs().get(0);

        assertEquals("fello", ch.name());
        assertEquals("tcp", ch.type());

        assertEquals(2, ch.client().endpoints().size());
        assertEquals("roundrobin", ch.client().strategy());

        assertNotNull(md.profiles().get("iso8583"));
    }

    // ===============================
    // 2️⃣ MISSING CHANNEL NAME
    // ===============================
    @Test
    void shouldFailWhenChannelNameMissing() {

        String dsl = """
            channel {
                type tcp
                server {
                    listen 127.0.0.1 26000
                    pool a
                }
                client {
                    connect b 26000
                }
            }
            """;

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> parser.parse(dsl));

        assertTrue(ex.getMessage().contains("channel.name"));
    }

    // ===============================
    // 3️⃣ DUPLICATE CLIENT ENDPOINT
    // ===============================
    @Test
    void shouldFailOnDuplicateClientEndpoint() {

        String dsl = """
            channel {
                name test
                type tcp

                server {
                    listen 127.0.0.1 26000
                    pool a
                }

                client {
                    connect x 26000
                    connect x 26000
                    strategy roundrobin
                }
            }
            """;

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> parser.parse(dsl));

        assertTrue(ex.getMessage().contains("duplicate client endpoint"));
    }

    // ===============================
    // 4️⃣ MULTI CONNECT WITHOUT STRATEGY
    // ===============================
    @Test
    void shouldFailWhenMultipleConnectWithoutStrategy() {

        String dsl = """
            channel {
                name test
                type tcp

                server {
                    listen 127.0.0.1 26000
                    pool a
                }

                client {
                    connect a 26000
                    connect b 26000
                }
            }
            """;

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> parser.parse(dsl));

        assertTrue(ex.getMessage().contains("client.strategy"));
    }

    // ===============================
    // 5️⃣ SERVER WITHOUT POOL
    // ===============================
    @Test
    void shouldFailWhenServerPoolEmpty() {

        String dsl = """
            channel {
                name test
                type tcp

                server {
                    listen 127.0.0.1 26000
                }

                client {
                    connect a 26000
                }
            }
            """;

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> parser.parse(dsl));

        assertTrue(ex.getMessage().contains("server.pool"));
    }

    // ===============================
    // 6️⃣ UNKNOWN PROFILE REFERENCE
    // ===============================
    @Test
    void shouldFailWhenProfileNotFound() {

        String dsl = """
            channel {
                name test
                type tcp

                server {
                    listen 127.0.0.1 26000
                    pool a
                }

                client {
                    connect b 26000
                }

                profile unknown
            }
            """;

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> parser.parse(dsl));

        assertTrue(ex.getMessage().contains("unknown profile"));
    }

    // ===============================
    // 7️⃣ EMPTY CORRELATION
    // ===============================
    @Test
    void shouldFailWhenCorrelationEmpty() {

        String dsl = """
            profile iso8583 {
                correlation {
                }
            }
            """;

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> parser.parse(dsl));

        assertTrue(ex.getMessage().contains("profile.correlation"));
    }

    // ===============================
    // 8️⃣ INVALID PORT
    // ===============================
    @Test
    void shouldFailOnInvalidPort() {

        String dsl = """
            channel {
                name test
                type tcp

                server {
                    listen 127.0.0.1 99999
                    pool a
                }

                client {
                    connect b 26000
                }
            }
            """;

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> parser.parse(dsl));

        assertTrue(ex.getMessage().contains("invalid server.listen port"));
    }

    // ===============================
    // 9️⃣ INVALID STRATEGY
    // ===============================
    @Test
    void shouldFailOnInvalidStrategy() {

        String dsl = """
            channel {
                name test
                type tcp

                server {
                    listen 127.0.0.1 26000
                    pool a
                    strategy invalidone
                }

                client {
                    connect b 26000
                }
            }
            """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(dsl));
    }
}
