package com.vomlabs.dialect.service.ai;

import com.vomlabs.dialect.config.DialectConfig;
import java.util.logging.Logger;

public class AiProviderFactory {

    private AiProviderFactory() {}

    public static AiProvider create(DialectConfig.AIConfig config, Logger logger) {
        if (config == null) {
            return new OpenRouterClient(
                new DialectConfig.AIConfig(false, "openrouter", "", "", "", 0.1, 5, 3, 500),
                logger
            );
        }
        return switch (config.provider().toLowerCase()) {
            case "openrouter" -> new OpenRouterClient(config, logger);
            case "openai" -> new OpenAiProvider(config, logger);
            case "anthropic" -> new AnthropicProvider(config, logger);
            case "gemini" -> new GeminiProvider(config, logger);
            case "huggingface" -> new HuggingFaceProvider(config, logger);
            default -> {
                logger.warning("Unknown AI provider '" + config.provider() + "', falling back to OpenRouter");
                yield new OpenRouterClient(config, logger);
            }
        };
    }
}
