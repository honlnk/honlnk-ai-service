package com.honlnk.ai.server.ai_server.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Configuration;

@Configuration
@ImportAutoConfiguration(exclude = {
    org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class,
    org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration.class
})
public class AiConfig {
}