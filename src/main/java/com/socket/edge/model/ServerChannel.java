package com.socket.edge.model;

import com.socket.edge.constant.ServerChannelField;
import com.socket.edge.constant.SockeEndpointField;
import com.socket.edge.model.helper.ClientChannelDiff;
import com.socket.edge.model.helper.FieldDiff;
import com.socket.edge.model.helper.ServerChannelDiff;
import com.socket.edge.model.helper.SocketEndpointDiff;
import com.socket.edge.utils.CommonUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ServerChannel(
        String listenHost,
        int listenPort,
        List<SocketEndpoint> pool,
        String strategy
) {

    public String id() {
        return listenHost + ":" + listenPort;
    }

    public ServerChannelDiff diffWith(ServerChannel newOne) {
        ServerChannel oldOne = this;

        // 1. Identified field changes
        Map<ServerChannelField, FieldDiff> changes = new LinkedHashMap<>();
        CommonUtil.diff(changes,
                ServerChannelField.LISTEN,
                oldOne.listenHost(), newOne.listenHost(),
                ChangeImpact.RESTART);
        CommonUtil.diff(changes,
                ServerChannelField.PORT,
                oldOne.listenPort(), newOne.listenPort(),
                ChangeImpact.RESTART);
        CommonUtil.diff(changes,
                ServerChannelField.STRATEGY,
                oldOne.strategy(), newOne.strategy(),
                ChangeImpact.LIVE);

        // 2. Map endpoint by ID
        Map<String, SocketEndpoint> oldMap = oldOne.pool.stream()
                .collect(Collectors.toMap(SocketEndpoint::host, e -> e));

        Map<String, SocketEndpoint> newMap = newOne.pool.stream()
                .collect(Collectors.toMap(SocketEndpoint::host, e -> e));

        // 3. Added
        List<SocketEndpoint> added = newMap.keySet().stream()
                .filter(host -> !oldMap.containsKey(host))
                .map(newMap::get)
                .toList();

        // 4. Removed
        List<SocketEndpoint> removed = oldMap.keySet().stream()
                .filter(host -> !newMap.containsKey(host))
                .map(oldMap::get)
                .toList();

        List<SocketEndpointDiff> modified =
                oldMap.keySet().stream()
                        .filter(newMap::containsKey)
                        .map(k ->
                                oldMap.get(k).diffWith(newMap.get(k))
                        )
                        .filter(SocketEndpointDiff::hasChanges)
                        .toList();

        return new ServerChannelDiff(oldOne, newOne, changes, added, removed, modified);
    };
}