package com.dbvein.analysis;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class NameNormalizer {
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9]+");
    private static final Set<String> WEAK_NAMES = Set.of(
            "ID", "STATE", "STATUS", "REMARK", "FLAG", "TYPE", "NAME", "CODE",
            "OPER", "DATE", "TIME", "AMOUNT", "BALANCE", "SORT", "ORDINAL"
    );

    private NameNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toUpperCase(Locale.ROOT);
        s = s.replace('-', '_');
        // camelCase -> snake then strip
        s = s.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        s = NON_ALNUM.matcher(s).replaceAll("");
        // common prefixes
        if (s.startsWith("T") && s.length() > 2 && Character.isDigit(s.charAt(1))) {
            // keep as is
        }
        if (s.startsWith("C_") || s.startsWith("F_")) {
            s = s.substring(2);
        }
        return s;
    }

    public static boolean isWeakName(String normalized) {
        return WEAK_NAMES.contains(normalized);
    }

    /** Strip trailing ID / NO / CODE suffix for fuzzy compare */
    public static String stem(String normalized) {
        if (normalized.endsWith("ID") && normalized.length() > 2) {
            return normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.endsWith("NO") && normalized.length() > 2) {
            return normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.endsWith("CODE") && normalized.length() > 4) {
            return normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.endsWith("NUM") && normalized.length() > 3) {
            return normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }
}
