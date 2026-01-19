package com.socket.edge.model.helper;

import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.Metadata;

import java.util.List;
import java.util.Set;

public record MetadataDiff(
                            Metadata oldMetadata,
                            Metadata newMetadata,
                            List<ChannelCfg> addedChannelCfgs,
                            List<ChannelCfg> deletedChannelCfgs,
                            List<ChannelCfgDiff> modifiedChannelCfg,
                            Set<String> addedProfiles,
                            Set<String> removedProfiles,
                            List<Iso8583ProfileDiff> modifiedProfiles
                           ) {

    public boolean hasChanges() {
        return !addedChannelCfgs.isEmpty()
                || !deletedChannelCfgs.isEmpty()
                || modifiedChannelCfg.stream().anyMatch(ChannelCfgDiff::hasChanges)
                || !addedProfiles.isEmpty()
                || !removedProfiles.isEmpty()
                || !modifiedProfiles.isEmpty();
    }

    public StringBuffer toString(StringBuffer sb) {
        if (!deletedChannelCfgs.isEmpty()) {
            deletedChannelCfgs.forEach(channel -> {
                sb.append("> Removed channel detected: ")
                        .append(" channel=").append(channel.name())
                        .append("\n");
            });
        }
        if (!addedChannelCfgs.isEmpty()) {
            addedChannelCfgs.forEach(channel ->{
                sb.append("> Added channel detected: ")
                        .append(" channel=").append(channel.name())
                        .append("\n");
            });
        }
        if (!modifiedChannelCfg.isEmpty()) {
            modifiedChannelCfg.forEach(channelCfgDiff -> {
                if (channelCfgDiff.hasChanges()) {
                    sb.append("> Modified channel detected: ")
                            .append(" channel=").append(channelCfgDiff.oldCfg().name())
                            .append("\n");
                    channelCfgDiff.toString(sb);
                }
            });
        }
        if (!removedProfiles.isEmpty()) {
            removedProfiles.forEach(profile -> {
                sb.append("> Removed profile detected: ")
                        .append(" profile=").append(profile)
                        .append("\n");
            });
        }
        if (!addedProfiles.isEmpty()) {
            addedProfiles.forEach(profile -> {
                sb.append("> Added profile detected: ")
                        .append(" profile=").append(profile)
                        .append("\n");
            });
        }
        if (!modifiedProfiles.isEmpty()) {
            modifiedProfiles.forEach(iso8583ProfileDiff -> {
                if (iso8583ProfileDiff.hasChanges()) {
                    sb.append("> Modified profile detected: ")
                            .append(" profileName=").append(iso8583ProfileDiff.profileName())
                            .append("\n");
                    iso8583ProfileDiff.toString(sb);
                }
            });
        }
        return sb;
    }
}
