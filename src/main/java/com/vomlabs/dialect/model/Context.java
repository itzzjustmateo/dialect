package com.vomlabs.dialect.model;

import java.util.Optional;

public record Context(
    LanguageMode mode,
    Optional<String> playerLanguage
) {
    public Context {
        if (mode == null) throw new IllegalArgumentException("mode must not be null");
        if (playerLanguage == null) playerLanguage = Optional.empty();
    }

    public static Context whitelist() {
        return new Context(LanguageMode.WHITELIST, Optional.empty());
    }

    public static Context blacklist() {
        return new Context(LanguageMode.BLACKLIST, Optional.empty());
    }

    public Context withPlayerLanguage(String language) {
        return new Context(mode, Optional.ofNullable(language));
    }

    public enum LanguageMode {
        WHITELIST,
        BLACKLIST
    }
}
