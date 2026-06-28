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
    String aiUnavailable,
    String languageDisabled,
    String languageSet,
    String actionbarTranslated,
    String actionbarDetected,
    String actionbarBlocked,
    String actionbarWarned,
    String actionbarAllowed,
    String actionbarProcessing
) {
    public MessagesConfig() {
        this(
            "<color:#478FC6>▸</color> <color:#C2C7D3>",
            "<color:#D1988C>✕ <color:#C2C7D3>Your message was not sent because it violates the server language policy.</color>",
            "<color:#C2C7D3>[</color><color:#478FC6>Translated from {from}</color><color:#C2C7D3>]: {message}</color>",
            "<color:#D1988C>✕ <color:#C2C7D3>You do not have permission to execute this command.</color>",
            "<color:#C2C7D3>Configuration and caches successfully reloaded.</color>",
            "<color:#C2C7D3>Did you mean?</color> <click:suggest_command:'{correction}'>"
            + "<hover:show_text:'<color:#C2C7D3>Click to accept</color>'><color:#478FC6>{correction}</color></hover></click>",
            "<color:#D1988C>✕ <color:#C2C7D3>An error occurred while processing your request. Please try again later.</color>",
            "<color:#D1988C>✕ <color:#C2C7D3>Too many requests. Please wait before sending another message.</color>",
            "<color:#D1988C>✕ <color:#C2C7D3>The AI service is unavailable. Please try again later.</color>",
            "<color:#D1988C>✕ <color:#C2C7D3>Language selection is not available on this server.</color>",
            "<color:#C2C7D3>Language set to</color> <color:#478FC6>{lang}</color>",
            "<color:#478FC6>⇄</color> <color:#C2C7D3>Translated</color> <color:#478FC6>{lang}</color>",
            "<color:#478FC6>◈</color> <color:#C2C7D3>Detected</color> <color:#478FC6>{lang}</color>",
            "<color:#CE5F4E>✕</color> <color:#C2C7D3>Message blocked</color>",
            "<color:#CE5F4E>⚠</color> <color:#C2C7D3>Language warning issued</color>",
            "<color:#478FC6>✓</color> <color:#C2C7D3>Message sent</color>",
            "<color:#478FC6>⋯</color> <color:#C2C7D3>Processing message...</color>"
        );
    }
}
