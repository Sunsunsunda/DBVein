package com.dbvein.metadata;

final class SqlIdent {
    private SqlIdent() {}

    static String oracle(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (!v.matches("[A-Za-z0-9_$#]+")) {
            throw new IllegalArgumentException("Invalid Oracle identifier: " + raw);
        }
        return "\"" + v.toUpperCase() + "\"";
    }

    static String mysql(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (!v.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid MySQL identifier: " + raw);
        }
        return "`" + v + "`";
    }
}
