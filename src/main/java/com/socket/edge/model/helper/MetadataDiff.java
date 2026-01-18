package com.socket.edge.model.helper;

import com.socket.edge.model.ChannelCfg;

import java.util.List;
import java.util.Set;

public record MetadataDiff(List<ChannelCfg> addedChannelCfgs,
                           List<ChannelCfg> deletedChannelCfgs,
                           List<ChannelCfgDiff> modifiedChannelCfg,
                           Set<String> addedProfiles,
                           Set<String> removedProfiles,
                           List<Iso8583ProfileDiff> modifiedProfiles
                           ) {
}
