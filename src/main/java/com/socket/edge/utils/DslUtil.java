package com.socket.edge.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class DslUtil {

    static String trimQuotes(String v) {
        return v.replaceAll("^\"|\"$", "");
    }

    static List<String> splitCsv(String v) {
        return Arrays.stream(v.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    static List<String> extractList(String v) {
        // ["0200","0210"]
        return Arrays.stream(
                        v.replace("[", "")
                                .replace("]", "")
                                .split(","))
                .map(String::trim)
                .map(DslUtil::trimQuotes)
                .collect(Collectors.toList());
    }
}
