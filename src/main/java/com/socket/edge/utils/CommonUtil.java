package com.socket.edge.utils;

import com.socket.edge.model.ChangeImpact;
import com.socket.edge.model.helper.FieldDiff;

import java.util.Map;
import java.util.Objects;

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
}
