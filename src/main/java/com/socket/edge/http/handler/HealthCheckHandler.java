package com.socket.edge.http.handler;

import com.socket.edge.constant.NodeRole;
import com.socket.edge.http.service.AdminHttpService;
import io.netty.handler.codec.http.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Legacy /healthcheck endpoint for backward compatibility.
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class HealthCheckHandler implements HttpServiceHandler {

    private final AdminHttpService adminHttpService;
    private final boolean clusterEnabled;

    public HealthCheckHandler(AdminHttpService adminHttpService, boolean clusterEnabled) {
        this.adminHttpService = adminHttpService;
        this.clusterEnabled = clusterEnabled;
    }

    @Override
    public String path() {
        return "/healthcheck";
    }

    @Override
    public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            NodeRole role = adminHttpService.getNodeRole();
            body.put("status", role == NodeRole.MASTER ? "OK" : "STANDBY");
            body.put("role", role.name());
            body.put("mode", clusterEnabled ? "CLUSTER" : "STANDALONE");
            return HttpServiceHandler.ok(body);
        } catch (Exception e) {
            return HttpServiceHandler.error(
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
