package com.socket.edge.core.iso;

import com.socket.edge.SystemBootstrap;
import com.socket.edge.core.MessageContext;
import com.socket.edge.constant.Direction;
import com.socket.edge.model.Iso8583Profile;
import com.socket.edge.model.ChannelCfg;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.socket.edge.SystemBootstrap.sc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Iso8583ProfileResolverTest {

    @InjectMocks
    private Iso8583ProfileResolver resolver;

    @Mock
    private MessageContext ctx;

    @Mock
    private Iso8583Profile profile;

    @Mock
    private ChannelCfg channelCfg;

    @BeforeAll
    static void initStaticConfig() {
        if (SystemBootstrap.sc == null) {
            SystemBootstrap.sc = ConfigFactory.parseString(
                    "message.packager.key = \"mti\""
            );
        }
    }

    // ======================
    // resolveDirection tests
    // ======================

    @Test
    void resolveDirection_shouldReturnDirection_whenMtiIsKnown() {
        // given
        String mtiKey = sc.getString("message.packager.key");
        when(ctx.field(mtiKey)).thenReturn("0200");

        // khusus INBOUND ada MTI
        when(profile.valuesFor(Direction.INBOUND))
                .thenReturn(Set.of("0200", "0210"));

        // when
        Direction result = resolver.resolveDirection(ctx, profile);

        // then
        assertEquals(Direction.INBOUND, result);
    }

    @Test
    void resolveDirection_shouldThrowException_whenMtiIsUnknown() {
        // given
        String mtiKey = sc.getString("message.packager.key");
        when(ctx.field(mtiKey)).thenReturn("9999");

        when(profile.valuesFor(any()))
                .thenReturn(Set.of("0200", "0210"));

        // when + then
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> resolver.resolveDirection(ctx, profile)
        );

        assertTrue(ex.getMessage().contains("Unknown MTI"));
    }

    @Test
    void resolveDirection_shouldThrowException_whenMtiIsNull() {
        // given
        String mtiKey = sc.getString("message.packager.key");
        when(ctx.field(mtiKey)).thenReturn(null);

        // when + then
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> resolver.resolveDirection(ctx, profile)
        );

        assertTrue(ex.getMessage().contains("MTI"));
    }

    // ==========================
    // buildCorrelationKey tests
    // ==========================

    @Test
    void buildCorrelationKey_shouldBuildKeySuccessfully() {
        when(ctx.getChannelCfg()).thenReturn(channelCfg);
        when(channelCfg.name()).thenReturn("TEST-CHANNEL");

        // given
        when(profile.correlationFields())
                .thenReturn(List.of("11", "37"));

        when(ctx.field("11")).thenReturn("123456");
        when(ctx.field("37")).thenReturn("ABCDEF");

        // when
        String key = resolver.buildCorrelationKey(ctx, profile);

        // then
        assertEquals("TEST-CHANNEL|123456|ABCDEF", key);
    }

    @Test
    void buildCorrelationKey_shouldThrowException_whenFieldMissing() {
        when(ctx.getChannelCfg()).thenReturn(channelCfg);
        when(channelCfg.name()).thenReturn("TEST-CHANNEL");

        // given
        when(profile.correlationFields())
                .thenReturn(List.of("11", "37"));

        when(ctx.field("11")).thenReturn("123456");
        when(ctx.field("37")).thenReturn(null);

        // when + then
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> resolver.buildCorrelationKey(ctx, profile)
        );

        assertTrue(ex.getMessage().contains("Correlation field missing"));
    }
}
