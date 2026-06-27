package com.vomlabs.dialect.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.regex.Pattern;

public final class ComponentExtractor {

    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private ComponentExtractor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String extractPlainText(Component component) {
        if (component == null) return "";
        return PLAIN_SERIALIZER.serialize(component);
    }

    public static String extractAndNormalize(Component component) {
        String text = extractPlainText(component);
        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
        return text;
    }

    public static boolean isEmpty(Component component) {
        if (component == null) return true;
        return extractAndNormalize(component).isEmpty();
    }

    public static int textLength(Component component) {
        return extractPlainText(component).length();
    }
}
