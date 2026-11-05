package com.oriontech.assistant.configuration;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig {

    @Bean
    OllamaChatModel ollamaChatModel(
            @Value("${app.ollama.base-url}") String baseUrl,
            @Value("${app.ollama.model}") String model,
            @Value("${app.ollama.temperature}") Double temperature,
            @Value("${app.ollama.timeout}") Duration timeout,
            @Value("${app.ollama.num-ctx:4096}") int numCtx) {

        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(temperature)
                .numCtx(numCtx)
                .timeout(timeout)
                .build();
    }

    @Bean
    OllamaStreamingChatModel ollamaStreamingChatModel(
            @Value("${app.ollama.base-url}") String baseUrl,
            @Value("${app.ollama.model}") String model,
            @Value("${app.ollama.temperature}") Double temperature,
            @Value("${app.ollama.timeout}") Duration timeout,
            @Value("${app.ollama.num-ctx:4096}") int numCtx) {

        return OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(temperature)
                .numCtx(numCtx)
                .timeout(timeout)
                .build();
    }
}
