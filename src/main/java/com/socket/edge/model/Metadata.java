package com.socket.edge.model;

import com.socket.edge.model.helper.ChannelCfgDiff;
import com.socket.edge.model.helper.Iso8583ProfileDiff;
import com.socket.edge.model.helper.MetadataDiff;
import com.socket.edge.model.helper.SocketEndpointDiff;

import java.util.*;
import java.util.stream.Collectors;

public record Metadata (
                List<ChannelCfg> channelCfgs,
                Map<String, Iso8583Profile> profiles)
{
    public void replaceWith(Metadata metadata) {
        channelCfgs.clear();
        profiles.clear();
        metadata.channelCfgs().forEach(k -> {
            channelCfgs.add(k);
        });
        metadata.profiles().forEach((k, v) -> {
            profiles.put(k, v);
        });
    }

    public MetadataDiff diffWith(Metadata newMd) {
        Metadata oldMd = this;

        // 1. ChannelCfg
        Map<String, ChannelCfg> oldMap = oldMd.channelCfgs.stream()
                .collect(Collectors.toMap(ChannelCfg::id, e -> e));

        Map<String, ChannelCfg> newMap = newMd.channelCfgs.stream()
                .collect(Collectors.toMap(ChannelCfg::id, e -> e));

        List<ChannelCfg> added = newMap.keySet().stream()
                .filter(id -> !oldMap.containsKey(id))
                .map(newMap::get)
                .toList();

        List<ChannelCfg> removed = oldMap.keySet().stream()
                .filter(id -> !newMap.containsKey(id))
                .map(oldMap::get)
                .toList();

        List<ChannelCfgDiff> modified =
                oldMap.keySet().stream()
                        .filter(newMap::containsKey)
                        .map(k ->
                                oldMap.get(k).diffWith(newMap.get(k))
                        )
                        .filter(ChannelCfgDiff::hasChanges)
                        .toList();

        // 2. Profile
        Map<String, Iso8583Profile> oldProfiles = oldMd.profiles();
        Map<String, Iso8583Profile> newProfiles = newMd.profiles();

        Set<String> addedProfiles = newProfiles.keySet().stream()
                .filter(p -> !oldProfiles.containsKey(p))
                .collect(Collectors.toSet());

        Set<String> removedProfiles = oldProfiles.keySet().stream()
                .filter(p -> !newProfiles.containsKey(p))
                .collect(Collectors.toSet());

        List<Iso8583ProfileDiff> modifiedProfiles =
                oldProfiles.keySet().stream()
                        .filter(newProfiles::containsKey)
                        .map(p ->
                                oldProfiles.get(p)
                                        .diffWith(p, newProfiles.get(p)))
                        .filter(Iso8583ProfileDiff::hasChanges)
                        .toList();

        return new MetadataDiff(
                oldMd,
                newMd,
                added,
                removed,
                modified,
                addedProfiles,
                removedProfiles,
                modifiedProfiles
        );
    }
}
