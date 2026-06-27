package com.vomlabs.dialect.config;

public record MessagesConfig(
    String prefix,
    String violationWarning,
    String translationFormat,
    String noPermission,
    String reloadSuccess,
    String correctionSuggested,
    String apiError,
    String rateLimited,
    String aiUnavailable
) {
    public MessagesConfig() {
        this(
            "<gradient:#4facfe:#00f2fe>[Dialect]</gradient> ",
            "<red>Your message was not sent because it violates the server language policy.</red>",
            "<gray>[Translated from {from}]: {message}</gray>",
            "<red>You do not have permission to execute this command.</red>",
            "<green>Configuration and caches successfully reloaded.</green>",
            "<gold>Did you mean:</gold> <click:suggest_command:'{correction}'><hover:show_text:'<gray>Click to accept</gray>'>{correction}</hover></click>",
            "<red>An error occurred while processing your request. Please try again later.</red>",
            "<red>Too many requests. Please wait before sending another message.</red>",
            "<red>The AI service is unavailable. Please try again later.</red>"
        );
    }
}
