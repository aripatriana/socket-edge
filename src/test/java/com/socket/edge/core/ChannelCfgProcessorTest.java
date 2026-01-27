package com.socket.edge.core;

import com.socket.edge.model.Metadata;
import com.socket.edge.utils.DslParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelCfgProcessorTest {
    private final DslParser parser = new DslParser();
    private final ChannelCfgProcessor processor = new ChannelCfgProcessor();

    // ==================================================
    // 1️⃣ HAPPY PATH
    // ==================================================
    @Test
    void shouldPassForValidMetadata() {

        Metadata md = parser.parse(validDsl());

        assertDoesNotThrow(() -> processor.validateMetadata(md));
    }

    // ==================================================
    // 2️⃣ NO CHANNEL DEFINED
    // ==================================================
    @Test
    void shouldFailWhenNoChannelDefined() {

        String dsl = """
            profile p {
                direction inbound {
                    de1 = "0200"
                }
                direction outbound {
                    de39 = "00"
                }
                correlation {
                    de11
                }
            }
            """;

        Metadata md = parser.parse(dsl);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class,
                        () -> processor.validateMetadata(md));

        assertTrue(ex.getMessage().contains("No channel defined"));
    }

    // ==================================================
    // 3️⃣ DUPLICATE CHANNEL NAME
    // ==================================================
    @Test
    void shouldFailOnDuplicateChannelName() {

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
                profile p
            }

            channel {
                name test
                type tcp
                server {
                    listen 127.0.0.1 26001
                    pool c
                }
                client {
                    connect d 26001
                }
                profile p
            }

            profile p {
                direction inbound {
                    de1 = "0200"
                }
                direction outbound {
                    de39 = "00"
                }
                correlation {
                    de11
                }
            }
            """;

        Metadata md = parser.parse(dsl);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class,
                        () -> processor.validateMetadata(md));

        assertTrue(ex.getMessage().contains("Duplicate channel name"));
    }

    // ==================================================
    // 4️⃣ INVALID CHANNEL TYPE
    // ==================================================
    @Test
    void shouldFailOnInvalidChannelType() {

        String dsl = """
            channel {
                name test
                type udp
                server {
                    listen 127.0.0.1 26000
                    pool a
                }
                client {
                    connect b 26000
                }
                profile p
            }

            profile p {
                direction inbound {
                    de1 = "0200"
                }
                direction outbound {
                    de39 = "00"
                }
                correlation {
                    de11
                }
            }
            """;

        Metadata md = parser.parse(dsl);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class,
                        () -> processor.validateMetadata(md));

        assertTrue(ex.getMessage().contains("invalid type"));
    }

    // ==================================================
    // 5️⃣ UNKNOWN PROFILE
    // ==================================================
    @Test
    void shouldFailOnUnknownProfile() {

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

        Metadata md = parser.parse(dsl);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class,
                        () -> processor.validateMetadata(md));

        assertTrue(ex.getMessage().contains("unknown profile"));
    }

    // ==================================================
    // 6️⃣ DUPLICATE SERVER LISTEN
    // ==================================================
    @Test
    void shouldFailOnDuplicateServerListen() {

        String dsl = """
            channel {
                name a
                type tcp
                server {
                    listen 127.0.0.1 26000
                    pool 127.0.0.1
                }
                client {
                    connect 127.0.0.1 26000
                }
                profile p
            }

            channel {
                name b
                type tcp
                server {
                    listen 127.0.0.1 26000
                    pool 127.0.0.1
                }
                client {
                    connect 127.0.0.1 26001
                }
                profile p
            }

            profile p {
                direction inbound {
                    de1 = "0200"
                }
                direction outbound {
                    de39 = "00"
                }
                correlation {
                    de11
                }
            }
            """;

        Metadata md = parser.parse(dsl);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class,
                        () -> processor.validateMetadata(md));

        assertTrue(ex.getMessage().contains("Duplicate server listen endpoint"));
    }

    // ==================================================
    // 7️⃣ DUPLICATE CLIENT ENDPOINT
    // ==================================================
    @Test
    void shouldFailOnDuplicateClientEndpoint() {

        String dsl = """
            channel {
                name test
                type tcp
                server {
                    listen 127.0.0.1 26000
                    pool 127.0.0.1
                }
                client {
                    connect 127.0.0.1 26000
                    connect 127.0.0.1 26000
                }
                profile p
            }

            profile p {
                direction inbound {
                    de1 = "0200"
                }
                direction outbound {
                    de39 = "00"
                }
                correlation {
                    de11
                }
            }
            """;

        Metadata md = parser.parse(dsl);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class,
                        () -> processor.validateMetadata(md));

        assertTrue(ex.getMessage().contains("Duplicate client endpoint"));
    }

    @Test
    void shouldFailOnInvalidClientStrategy() {

        String dsl = """
            channel {
                name test
                type tcp
                server {
                    listen 127.0.0.1 26000
                    pool 127.0.0.1
                }
                client {
                    connect 127.0.0.1 26000
                    strategy invalid
                }
                profile p
            }

            profile p {
                direction inbound {
                    de1 = "0200"
                }
                direction outbound {
                    de39 = "00"
                }
                correlation {
                    de11
                }
            }
            """;

        Metadata md = parser.parse(dsl);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class,
                        () -> processor.validateMetadata(md));

        assertTrue(ex.getMessage().contains("Unknown client strategy"));
    }

    @Test
    void shouldFailOnInvalidInvalidIPAddress() {

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
                    strategy invalid
                }
                profile p
            }

            profile p {
                direction inbound {
                    de1 = "0200"
                }
                direction outbound {
                    de39 = "00"
                }
                correlation {
                    de11
                }
            }
            """;

        Metadata md = parser.parse(dsl);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class,
                        () -> processor.validateMetadata(md));

        assertTrue(ex.getMessage().contains("Invalid IP address"));
    }

    @Test
    void shouldFailWhenOutboundMissing() {

        String dsl = """
            channel {
                name test
                type tcp
                server {
                    listen 127.0.0.1 26000
                    pool 127.0.0.1
                }
                client {
                    connect 127.0.0.1 26000
                }
                profile p
            }

            profile p {
                direction inbound {
                    de1 = "0200"
                }
                correlation {
                    de11
                }
            }
            """;

        Metadata md = parser.parse(dsl);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class,
                        () -> processor.validateMetadata(md));

        assertTrue(ex.getMessage().contains("outbound"));
    }

    private String validDsl() {
        return """
            channel {
                name test
                type tcp
                server {
                    listen 127.0.0.1 26000
                    pool 127.0.0.1
                }
                client {
                    connect 127.0.0.1 26000
                    strategy roundrobin
                }
                profile p
            }

            profile p {
                direction inbound {
                    de1 = "0200"
                }
                direction outbound {
                    de39 = "00"
                }
                correlation {
                    de11
                }
            }
            """;
    }
}
