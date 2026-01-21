package com.socket.edge.utils;

import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.Metadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DslParserTest {

    private final DslParser parser = new DslParser();

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

    @Test
    void shouldFailWhenServerBlockMissing() {

        String dsl = """
        channel {
            name test
            type tcp
            client {
                connect a 26000
            }
        }
        """;

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> parser.parse(dsl));

        assertTrue(ex.getMessage().contains("channel.server block is required"));
    }

    @Test
    void shouldIgnoreBraceInsideComment() {
        String dsl = """
        # this is comment {
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
        }
        """;

        Metadata md = parser.parse(dsl);
        assertEquals(1, md.channelCfgs().size());
    }

    @Test
    void shouldFailWhenClientBlockMissing() {

        String dsl = """
        channel {
            name test
            type tcp
            server {
                listen 127.0.0.1 26000
                pool a
            }
        }
        """;

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> parser.parse(dsl));

        assertTrue(ex.getMessage().contains("channel.client block is required"));
    }


    @Test
    void shouldFailOnDuplicateChannelName() {
        String dsl = """
        channel {
            name a
            type tcp
            server { listen 127.0.0.1 1 pool x }
            client { connect y 1 }
        }

        channel {
            name a
            type tcp
            server { listen 127.0.0.1 2 pool x }
            client { connect y 2 }
        }
        """;

        assertThrows(IllegalStateException.class, () -> parser.parse(dsl));
    }

    @Test
    void shouldFailWhenInboundMissing() {
        String dsl = """
        profile iso8583 {
            correlation {
                de11
            }
        }
        """;

        assertThrows(IllegalStateException.class, () -> parser.parse(dsl));
    }

}
