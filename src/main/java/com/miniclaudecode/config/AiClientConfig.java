package com.miniclaudecode.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Dual-backend configuration.
 * Spring AI 1.0.0 auto-configures AnthropicChatModel and OpenAiChatModel
 * via starters when the corresponding API keys are set in application properties.
 * This config just picks the preferred one (Anthropic first) when both are available.
 */
@Configuration
public class AiClientConfig {

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "primaryChatModel")
    ChatModel primaryChatModel(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            AnthropicChatModel anthropicChatModel,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            OpenAiChatModel openAiChatModel) {

        if (anthropicChatModel != null) {
            return anthropicChatModel;
        }
        if (openAiChatModel != null) {
            return openAiChatModel;
        }
        throw new IllegalStateException(
                "No API key configured. Set spring.ai.anthropic.api-key or spring.ai.openai.api-key.");
    }
}
