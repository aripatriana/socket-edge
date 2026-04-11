package com.socket.edge.utils;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOException;

import java.util.HashMap;
import java.util.Map;

/**
 * ISO 8583 message parser.
 *
 * <p>v3.0 changes:
 * <ul>
 *   <li>Fixed: Now extracts ALL present fields dynamically instead of
 *       hardcoding only 5 fields (de2, de11, de12, de13, de37).
 *       This ensures correlation fields defined in profile config
 *       are always available regardless of field number.</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class IsoParser {

    private final ISOPackager packager;

    public IsoParser(ISOPackager packager) {
        this.packager = packager;
    }

    /**
     * Parses raw ISO 8583 bytes into a map of field key → value.
     *
     * <p>Field keys follow the convention {@code deN} where N is
     * the ISO field number (e.g. {@code de2}, {@code de11}, {@code de37}).</p>
     *
     * <p>The MTI is stored under key {@code de1}.</p>
     *
     * @param message raw ISO 8583 message bytes
     * @return map of field keys to values
     * @throws IllegalArgumentException if the message cannot be parsed
     */
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

        // MTI (jPOS stores MTI separately, not as a numbered field)
        map.put("de1", iso.getMTI());

        // v3.0: Extract ALL present fields dynamically
        for (int i = 2; i <= iso.getMaxField(); i++) {
            if (iso.hasField(i)) {
                map.put("de" + i, iso.getString(i));
            }
        }

        return map;
    }
}
