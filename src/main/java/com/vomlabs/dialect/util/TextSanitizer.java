package com.vomlabs.dialect.util;

import java.util.regex.Pattern;

public final class TextSanitizer {

    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final Pattern EXCESSIVE_WHITESPACE = Pattern.compile("\\s{2,}");
    private static final Pattern REPEATED_CHARACTERS = Pattern.compile("(.)\\1{10,}");
    private static final int MAX_MESSAGE_LENGTH = 1024;
    private static final int MAX_REPEATED_CHARS = 8;

    private TextSanitizer() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String sanitize(String input) {
        if (input == null) return "";
        String result = CONTROL_CHARS.matcher(input).replaceAll("");
        result = REPEATED_CHARACTERS.matcher(result).replaceAll("$1".repeat(Math.min(MAX_REPEATED_CHARS, 3)));
        result = EXCESSIVE_WHITESPACE.matcher(result).replaceAll(" ");
        result = result.trim();
        if (result.length() > MAX_MESSAGE_LENGTH) {
            result = result.substring(0, MAX_MESSAGE_LENGTH);
        }
        return result;
    }

    public static String truncate(String input, int maxLength) {
        if (input == null) return "";
        if (input.length() <= maxLength) return input;
        return input.substring(0, maxLength);
    }

    public static boolean isTooLong(String input) {
        return input != null && input.length() > MAX_MESSAGE_LENGTH;
    }
}
