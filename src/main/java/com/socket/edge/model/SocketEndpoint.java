package com.socket.edge.model;

import com.socket.edge.constant.SockeEndpointField;
import com.socket.edge.core.strategy.WeightedCandidate;
import com.socket.edge.model.helper.FieldDiff;
import com.socket.edge.model.helper.SocketEndpointDiff;
import com.socket.edge.utils.CommonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

public record SocketEndpoint(
        String host,
        int port,
        int weight,
        int priority
) implements WeightedCandidate {
    @Override
    public int getWeight() {
        return weight;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public String id() {
        return host + ":" + port;
    }

    public SocketEndpointDiff diffWith(SocketEndpoint newOne) {
        SocketEndpoint oldOne = this;
        Map<SockeEndpointField, FieldDiff> changes = new LinkedHashMap<>();
        CommonUtil.diff(changes,
                SockeEndpointField.WEIGHT,
                oldOne.weight(), newOne.weight(),
                ChangeImpact.LIVE);
        CommonUtil.diff(changes,
                SockeEndpointField.PRIORITY,
                oldOne.priority(), newOne.priority(),
                ChangeImpact.RESTART);
        return new SocketEndpointDiff(changes);
    }
}