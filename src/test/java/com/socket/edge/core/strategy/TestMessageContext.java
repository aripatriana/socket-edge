package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;

import java.util.HashMap;
import java.util.Map;

public class TestMessageContext extends MessageContext {

    private final Map<String, String> fields = new HashMap<>();

    public TestMessageContext() {
        super(null, null);
    }

    public TestMessageContext(Map<String, String> isoFields, byte[] rawBytes) {
        super(isoFields, rawBytes);
    }

    TestMessageContext with(String key, String value) {
        fields.put(key, value);
        return this;
    }

    @Override
    public String field(String key) {
        return fields.get(key);
    }
}