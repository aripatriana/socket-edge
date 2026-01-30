package com.socket.edge.model;

import com.socket.edge.constant.RolePreference;

public record RolePolicy(
        RolePreference prefer,
        boolean strict
) {
    public boolean allowMasterElection() {
        return prefer != RolePreference.SLAVE;
    }

    public boolean failIfNotMaster() {
        return prefer == RolePreference.MASTER && strict;
    }
}
