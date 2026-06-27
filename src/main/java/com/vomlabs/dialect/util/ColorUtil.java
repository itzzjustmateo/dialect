package com.vomlabs.dialect.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();
    private static final Map<String, Component> COMPONENT_CACHE = new ConcurrentHashMap<>();

    private ColorUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Component deserialize(String miniMessageString) {
        if (miniMessageString == null || miniMessageString.isBlank()) {
            return Component.empty();
        }
        return COMPONENT_CACHE.computeIfAbsent(miniMessageString, key ->
            MINI_MESSAGE.deserialize(key).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
        );
    }

    public static Component deserializeUncached(String miniMessageString) {
        if (miniMessageString == null || miniMessageString.isBlank()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(miniMessageString)
            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String serialize(Component component) {
        if (component == null) return "";
        return MINI_MESSAGE.serialize(component);
    }

    public static String toPlainText(Component component) {
        if (component == null) return "";
        return PLAIN_SERIALIZER.serialize(component);
    }

    public static Component replaceText(Component component, String target, String replacement) {
        if (component == null) return Component.empty();
        return component.replaceText(builder ->
            builder.matchLiteral(target).replacement(replacement)
        );
    }

    public static Component join(Component separator, Component... components) {
        return Component.join(
            JoinConfiguration.separator(separator),
            java.util.List.of(components)
        );
    }
}
