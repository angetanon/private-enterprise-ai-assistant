package com.oriontech.assistant.configuration;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConfigTest {

    private final LlmConfig config = new LlmConfig();
    private final String BASE_URL = "http://localhost:11434";
    private final String LLM_MODEL = "llama3.2:3b";
    private final Double TEMPERATURE = 0.3;
    private final int NUM_CTX = 4096;
    @Test
    void ollamaChatModel_estConstruit() {
        OllamaChatModel model = config.ollamaChatModel(
                BASE_URL, LLM_MODEL, TEMPERATURE, Duration.ofMinutes(5), NUM_CTX);

        assertThat(model).isNotNull();
    }

    @Test
    void ollamaStreamingChatModel_estConstruit() {
        OllamaStreamingChatModel model = config.ollamaStreamingChatModel(
                BASE_URL, LLM_MODEL, TEMPERATURE, Duration.ofMinutes(5), NUM_CTX);

        assertThat(model).isNotNull();
    }
}

