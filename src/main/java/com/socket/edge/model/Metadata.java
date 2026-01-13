package com.socket.edge.model;

import java.util.List;
import java.util.Map;

public record Metadata(
        List<ChannelCfg> channelCfgs,
        Map<String, Iso8583Profile> profiles
) {}
