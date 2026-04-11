package com.socket.edge.model;

import com.socket.edge.constant.SocketEndpointField;
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
        int priority,
        int maxfails,
        int failTimeout
) implements WeightedCandidate {
    @Override
    public int getWeight() {
        return weight;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public EndpointKey id() {
        return EndpointKey.from(this);
    }

    public SocketEndpointDiff diffWith(SocketEndpoint newOne) {
        SocketEndpoint oldOne = this;
        Map<SocketEndpointField, FieldDiff> changes = new LinkedHashMap<>();
        CommonUtil.diff(changes,
                SocketEndpointField.WEIGHT,
                oldOne.weight(), newOne.weight(),
                ChangeImpact.LIVE);
        CommonUtil.diff(changes,
                SocketEndpointField.PRIORITY,
                oldOne.priority(), newOne.priority(),
                ChangeImpact.LIVE);
        CommonUtil.diff(changes,
                SocketEndpointField.MAX_FAILS,
                oldOne.maxfails(), newOne.maxfails(),
                ChangeImpact.LIVE);
        CommonUtil.diff(changes,
                SocketEndpointField.FAIL_TIMEOUT,
                oldOne.failTimeout(), newOne.failTimeout(),
                ChangeImpact.LIVE);
        return new SocketEndpointDiff(oldOne, newOne, changes);
    }
}