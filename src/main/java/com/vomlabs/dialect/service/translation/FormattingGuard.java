package com.vomlabs.dialect.service.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormattingGuard {

    private static final Pattern MINECRAFT_COLOR_PATTERN = Pattern.compile("(?i)(?:§|&)[0-9a-fk-or]");
    private static final Pattern MINIMESSAGE_TAG_PATTERN = Pattern.compile("<[/]?[a-zA-Z][a-zA-Z0-9_:#]*(?::[^>]*)?>");
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\w{3,16}");
    private static final Pattern ALL_PATTERNS = Pattern.compile(
        "(?i)(?:§|&)[0-9a-fk-or]|<[/]?[a-zA-Z][a-zA-Z0-9_:#]*(?::[^>]*)?>|@\\w{3,16}"
    );

    private final List<Token> tokens;

    public FormattingGuard() {
        this.tokens = new ArrayList<>();
    }

    public String tokenize(String input) {
        if (input == null || input.isEmpty()) return input;
        tokens.clear();

        Matcher matcher = ALL_PATTERNS.matcher(input);
        StringBuffer result = new StringBuffer();
        int index = 0;

        while (matcher.find()) {
            String match = matcher.group();
            String placeholder = "__TAG_" + index + "__";
            tokens.add(new Token(placeholder, match, matcher.start()));
            matcher.appendReplacement(result, Matcher.quoteReplacement(placeholder));
            index++;
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public String restore(String translatedInput) {
        if (translatedInput == null || translatedInput.isEmpty() || tokens.isEmpty()) {
            return translatedInput;
        }

        String result = translatedInput;
        for (Token token : tokens) {
            result = result.replace(token.placeholder(), token.original());
        }
        return result;
    }

    public List<Token> getTokens() {
        return List.copyOf(tokens);
    }

    public void clear() {
        tokens.clear();
    }

    public static String process(String input, java.util.function.UnaryOperator<String> translationFunction) {
        FormattingGuard guard = new FormattingGuard();
        String tokenized = guard.tokenize(input);
        String translated = translationFunction.apply(tokenized);
        return guard.restore(translated);
    }

    public record Token(String placeholder, String original, int position) {}
}
