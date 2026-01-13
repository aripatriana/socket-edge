package com.socket.edge.utils;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOException;

import java.util.HashMap;
import java.util.Map;

public final class IsoParser {

    private final ISOPackager packager;

    public IsoParser(ISOPackager packager) {
        this.packager = packager;
    }

    public Map<String, String> parse(byte[] message) {

        try {
            ISOMsg iso = new ISOMsg();
            iso.setPackager(packager);
            iso.unpack(message);

            return extractFields(iso);

        } catch (ISOException e) {
            throw new IllegalArgumentException("Invalid ISO8583 message", e);
        }
    }

    private Map<String, String> extractFields(ISOMsg iso) throws ISOException {

        Map<String, String> map = new HashMap<>();

        // MTI (jPOS: bukan field number)
        map.put("de1", iso.getMTI());

        // Field-field correlation (ambil hanya yang diperlukan)
        putIfPresent(map, "de2", iso, 2);
        putIfPresent(map, "de11", iso, 11);
        putIfPresent(map, "de12", iso, 12);
        putIfPresent(map, "de13", iso, 13);
        putIfPresent(map, "de37", iso, 37);

        return map;
    }

    private void putIfPresent(
            Map<String, String> map,
            String key,
            ISOMsg iso,
            int field
    ) {
        if (iso.hasField(field)) {
            map.put(key, iso.getString(field));
        }
    }
}