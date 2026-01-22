package com.socket.edge.utils;

import com.socket.edge.model.ChangeImpact;
import com.socket.edge.model.helper.FieldDiff;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

public class CommonUtil {

    public static <T extends Enum<T>> void diff(
            Map<T, FieldDiff> map,
            T field,
            Object oldV,
            Object newV,
            ChangeImpact impact) {

        if (!Objects.equals(oldV, newV)) {
            map.put(field, new FieldDiff(oldV, newV, impact));
        }
    }

    public static String serverId(String name, int port) {
        return String.format("%s-server-%d",name, port);
    }

    public static String clientId(String name, String host, int port) {
        return String.format("%s-client-%s-%d", name, host, port);
    }

    public static String channelId(String socketId, String endpointId) {
        return String.format("%08d", identity(socketId, endpointId));
    }

    public static int identity(String id1, String id2) {
        String combined = id1 + "|" + id2;

        CRC32 crc = new CRC32();
        crc.update(combined.getBytes(StandardCharsets.UTF_8));

        return (int) (crc.getValue() % 100_000_000);
    }
}
