package com.socket.edge.model;

import com.socket.edge.model.helper.*;
import com.socket.edge.constant.ChannelCfgField;
import com.socket.edge.utils.CommonUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Channel configuration record.
 *
 * <p>v3.0 changes:
 * <ul>
 *   <li>{@code profile} (String) → {@code profiles} (List&lt;String&gt;)
 *       — supports multiple profiles per channel</li>
 *   <li>Added {@code unknownMti} — behaviour when MTI not found
 *       in any profile ("reject")</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public record ChannelCfg(
        String name,
        String type,
        ServerChannel server,
        ClientChannel client,
        List<String> profiles,
        String unknownMti
) {

    String id() {
        return name;
    }

    public ChannelCfgDiff diffWith(ChannelCfg newCfg) {
        ChannelCfg oldCfg = this;
        Map<ChannelCfgField, FieldDiff> changes = new LinkedHashMap<>();

        CommonUtil.diff(changes,
                ChannelCfgField.NAME,
                oldCfg.name(), newCfg.name(),
                ChangeImpact.RESTART);
        CommonUtil.diff(changes,
                ChannelCfgField.TYPE,
                oldCfg.type(), newCfg.type(),
                ChangeImpact.LIVE);
        CommonUtil.diff(changes,
                ChannelCfgField.PROFILES,
                oldCfg.profiles(), newCfg.profiles(),
                ChangeImpact.LIVE);
        CommonUtil.diff(changes,
                ChannelCfgField.UNKNOWN_MTI,
                oldCfg.unknownMti(), newCfg.unknownMti(),
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
