package com.socket.edge.model;

import com.socket.edge.model.helper.*;
import com.socket.edge.constant.ChannelCfgField;
import com.socket.edge.utils.CommonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

public record ChannelCfg (String name,
                        String type,
                        ServerChannel server,
                        ClientChannel client, String profile) {

    String id() {
        return name;
    }

    public ChannelCfgDiff diffWith(ChannelCfg newCfg) {
        ChannelCfg oldCfg = this;
        Map<ChannelCfgField, FieldDiff> changes = new LinkedHashMap<>();

        CommonUtil.diff(changes,
                ChannelCfgField.NAME,
                oldCfg.type(), newCfg.type(),
                ChangeImpact.RESTART);
        CommonUtil.diff(changes,
                ChannelCfgField.TYPE,
                oldCfg.type(), newCfg.type(),
                ChangeImpact.LIVE);
        CommonUtil.diff(changes,
                ChannelCfgField.PROFILE,
                oldCfg.profile(), newCfg.profile(),
                ChangeImpact.LIVE);

        ServerChannelDiff serverChannelDiff = oldCfg.server().diffWith(newCfg.server);
        ClientChannelDiff clientChannelDiff = oldCfg.client().diffWith(newCfg.client);

        return new ChannelCfgDiff(
                oldCfg,
                newCfg,
                changes,
                serverChannelDiff,
                clientChannelDiff
        );
    }
}