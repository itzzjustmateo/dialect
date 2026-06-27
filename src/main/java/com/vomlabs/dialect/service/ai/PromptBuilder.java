package com.vomlabs.dialect.service.ai;

import java.util.ArrayList;
import java.util.List;

public class PromptBuilder {

    private final List<Message> messages;

    public PromptBuilder() {
        this.messages = new ArrayList<>();
    }

    public PromptBuilder withSystemPrompt(String prompt) {
        messages.add(new Message("system", prompt));
        return this;
    }

    public PromptBuilder withUserPrompt(String prompt) {
        messages.add(new Message("user", prompt));
        return this;
    }

    public PromptBuilder withAssistantPrompt(String prompt) {
        messages.add(new Message("assistant", prompt));
        return this;
    }

    public List<Message> build() {
        return List.copyOf(messages);
    }

    public static PromptBuilder createDetectionPrompt(String message) {
        return new PromptBuilder()
            .withSystemPrompt(
                "You are a language detection assistant. Your task is to analyze the given text and respond with ONLY a "
                + "minified JSON object. Do not include any other text, explanation, or formatting. "
                + "The JSON must follow this exact structure:\n"
                + "{\n"
                + "  \"detected_language\": \"ISO 639-1 code\",\n"
                + "  \"confidence\": 0.00 to 1.00,\n"
                + "  \"contains_slang\": true/false,\n"
                + "  \"is_valid_slang_in_context\": true/false,\n"
                + "  \"normalized_translation\": \"string or null\"\n"
                + "}\n\n"
                + "Rules:\n"
                + "- detected_language must be a valid 2-letter ISO 639-1 code\n"
                + "- confidence must be a number between 0.00 and 1.00\n"
                + "- contains_slang indicates if the text contains slang or informal language\n"
                + "- is_valid_slang_in_context indicates if slang usage is appropriate for the context\n"
                + "- normalized_translation: if the text is in a language other than English, provide an English translation; "
                + "if it's already English or contains mixed language, provide null\n"
                + "- Respond with ONLY the JSON object, no other text."
            )
            .withUserPrompt("Analyze this text: \"" + sanitizeForJson(sanitizeMessage(message)) + "\"");
    }

    public static PromptBuilder createTranslationPrompt(String text, String sourceLanguage, String targetLanguage) {
        return new PromptBuilder()
            .withSystemPrompt(
                "You are a professional translator. Translate the given text from " + sourceLanguage + " to " + targetLanguage + ". "
                + "Preserve the original formatting, tone, and style as much as possible. "
                + "Respond with ONLY the translated text, no explanations or additional content."
            )
            .withUserPrompt("Translate from " + sourceLanguage + " to " + targetLanguage + ": \"" + sanitizeForJson(text) + "\"");
    }

    public static PromptBuilder createModerationPrompt(String message, String language, String rules) {
        return new PromptBuilder()
            .withSystemPrompt(
                "You are a chat moderation assistant. Analyze the following message for compliance with server rules. "
                + "Respond with ONLY a minified JSON object:\n"
                + "{\n"
                + "  \"is_allowed\": true/false,\n"
                + "  \"reason\": \"string or null\",\n"
                + "  \"severity\": \"low|medium|high\"\n"
                + "}\n\n"
                + "Rules: " + rules
            )
            .withUserPrompt("Language: " + language + "\nMessage: \"" + sanitizeForJson(sanitizeMessage(message)) + "\"");
    }

    private static String sanitizeForJson(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static String sanitizeMessage(String message) {
        if (message.length() > 500) {
            return message.substring(0, 500);
        }
        return message;
    }

    public record Message(String role, String content) {}
}
