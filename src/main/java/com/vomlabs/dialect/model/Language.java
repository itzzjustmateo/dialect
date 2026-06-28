package com.vomlabs.dialect.model;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public record Language(String code, String displayName) {

    private static final Map<String, Language> CACHE = new ConcurrentHashMap<>();

    public static final Language ENGLISH = new Language("en", "English");
    public static final Language SPANISH = new Language("es", "Spanish");
    public static final Language FRENCH = new Language("fr", "French");
    public static final Language GERMAN = new Language("de", "German");
    public static final Language ITALIAN = new Language("it", "Italian");
    public static final Language PORTUGUESE = new Language("pt", "Portuguese");
    public static final Language RUSSIAN = new Language("ru", "Russian");
    public static final Language CHINESE = new Language("zh", "Chinese");
    public static final Language JAPANESE = new Language("ja", "Japanese");
    public static final Language KOREAN = new Language("ko", "Korean");
    public static final Language ARABIC = new Language("ar", "Arabic");
    public static final Language DUTCH = new Language("nl", "Dutch");
    public static final Language POLISH = new Language("pl", "Polish");
    public static final Language TURKISH = new Language("tr", "Turkish");
    public static final Language VIETNAMESE = new Language("vi", "Vietnamese");
    public static final Language THAI = new Language("th", "Thai");
    public static final Language SWEDISH = new Language("sv", "Swedish");
    public static final Language DANISH = new Language("da", "Danish");
    public static final Language FINNISH = new Language("fi", "Finnish");
    public static final Language NORWEGIAN = new Language("no", "Norwegian");
    public static final Language CZECH = new Language("cs", "Czech");
    public static final Language HUNGARIAN = new Language("hu", "Hungarian");
    public static final Language ROMANIAN = new Language("ro", "Romanian");
    public static final Language UKRAINIAN = new Language("uk", "Ukrainian");
    public static final Language GREEK = new Language("el", "Greek");
    public static final Language HEBREW = new Language("he", "Hebrew");
    public static final Language HINDI = new Language("hi", "Hindi");
    public static final Language INDONESIAN = new Language("id", "Indonesian");
    public static final Language MALAY = new Language("ms", "Malay");

    private static final Set<Language> COMMON_LANGUAGES = Set.of(
        ENGLISH, SPANISH, FRENCH, GERMAN, ITALIAN, PORTUGUESE, RUSSIAN,
        CHINESE, JAPANESE, KOREAN, ARABIC, DUTCH, POLISH, TURKISH,
        VIETNAMESE, THAI, SWEDISH, DANISH, FINNISH, NORWEGIAN, CZECH,
        HUNGARIAN, ROMANIAN, UKRAINIAN, GREEK, HEBREW, HINDI, INDONESIAN, MALAY
    );

    public Language {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Language code must not be null or blank");
        }
        code = code.toLowerCase(Locale.ROOT).trim();
        if (code.length() != 2) {
            throw new IllegalArgumentException("Language code must be a 2-letter ISO 639-1 code");
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = code.toUpperCase(Locale.ROOT);
        }
    }

    public static Optional<Language> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.toLowerCase(Locale.ROOT).trim();
        return Optional.ofNullable(
            CACHE.computeIfAbsent(normalized, key -> {
                Locale locale = Locale.of(key);
                String name = locale.getDisplayName(Locale.ENGLISH);
                if (name.equalsIgnoreCase(key)) {
                    for (Language lang : COMMON_LANGUAGES) {
                        if (lang.code().equals(key)) {
                            return lang;
                        }
                    }
                    return null;
                }
                return new Language(key, name);
            })
        );
    }

    public static boolean isValid(String code) {
        return fromCode(code).isPresent();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Language language)) {
            return false;
        }
        return code.equals(language.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code + " (" + displayName + ")";
    }
}
