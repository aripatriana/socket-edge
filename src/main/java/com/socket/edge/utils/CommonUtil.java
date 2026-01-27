package com.socket.edge.utils;

import com.socket.edge.model.ChangeImpact;
import com.socket.edge.model.helper.FieldDiff;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class CommonUtil {

    private static final Pattern IPV4 =
            Pattern.compile(
                    "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}"
                            + "(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    private static final Pattern IPV6 =
            Pattern.compile(
                    "^(" +
                            "([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|" +          // full
                            "([0-9a-fA-F]{1,4}:){1,7}:|" +                      // :: short
                            "([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|" +
                            "([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|" +
                            "([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|" +
                            "([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|" +
                            "([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|" +
                            "[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|" +
                            ":((:[0-9a-fA-F]{1,4}){1,7}|:)" +
                            ")$");

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

    public static String hashId(String socketId, String endpointId) {
        return String.format("%08d", identity(socketId, endpointId));
    }

    public static int identity(String id1, String id2) {
        String combined = id1 + "|" + id2;

        CRC32 crc = new CRC32();
        crc.update(combined.getBytes(StandardCharsets.UTF_8));

        return (int) (crc.getValue() % 100_000_000);
    }

    public static boolean validIPAddresss(String host) {
        return IPV4.matcher(host).matches() || IPV6.matcher(host).matches();
    }

}
