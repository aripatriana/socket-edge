package com.socket.edge.model;

import com.socket.edge.constant.ClientChannelField;
import com.socket.edge.model.helper.ClientChannelDiff;
import com.socket.edge.model.helper.FieldDiff;
import com.socket.edge.model.helper.SocketEndpointDiff;
import com.socket.edge.utils.CommonUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ClientChannel(
        List<SocketEndpoint> endpoints,
        String strategy
) {

    public ClientChannelDiff diffWith(ClientChannel newOne) {
        ClientChannel oldOne = this;

        // 1. Identified field changes
        Map<ClientChannelField, FieldDiff> changes = new LinkedHashMap<>();
        CommonUtil.diff(changes,
                ClientChannelField.STRATEGY,
                oldOne.strategy(), newOne.strategy(),
                ChangeImpact.LIVE);

        // 2. Map endpoint by ID
        Map<String, SocketEndpoint> oldMap = this.endpoints.stream()
                .collect(Collectors.toMap(SocketEndpoint::id, e -> e));

        Map<String, SocketEndpoint> newMap = newOne.endpoints.stream()
                .collect(Collectors.toMap(SocketEndpoint::id, e -> e));

        // 3. Added
        List<SocketEndpoint> added = newMap.keySet().stream()
                .filter(id -> !oldMap.containsKey(id))
                .map(newMap::get)
                .toList();

        // 4. Removed
        List<SocketEndpoint> removed = oldMap.keySet().stream()
                .filter(id -> !newMap.containsKey(id))
                .map(oldMap::get)
                .toList();

        // 5. Identify socket endpoint changes
        List<SocketEndpointDiff> modified =
                oldMap.keySet().stream()
                        .filter(newMap::containsKey)
                        .map(k ->
                                oldMap.get(k).diffWith(newMap.get(k))
                        )
                        .filter(SocketEndpointDiff::hasChanges)
                        .toList();

        return new ClientChannelDiff(oldOne, newOne, changes, added, removed, modified);
    };
}