package com.oriontech.assistant.configuration;

import com.oriontech.assistant.ai.AiStack;
import com.oriontech.assistant.memory.PostgresChatMemoryStore;
import com.oriontech.assistant.service.EnterpriseAssistantService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class AiStacksConfig {

    private final PostgresChatMemoryStore memoryStore;
    @Value("${app.pgvector.host}")
    private String pgHost;
    @Value("${app.pgvector.port}")
    private int pgPort;
    @Value("${app.pgvector.database}")
    private String pgDatabase;
    @Value("${app.pgvector.user}")
    private String pgUser;
    @Value("${app.pgvector.password}")
    private String pgPassword;
    @Value("${app.rag.max-results}")
    private int maxResults;

    // ---------------------------------------------------------------- OLLAMA

    @Bean
    AiStack ollamaStack(
            @Value("${app.ollama.base-url}") String baseUrl,
            @Value("${app.ollama.chat-model}") String chatModelName,
            @Value("${app.ollama.embedding-model}") String embeddingModelName,
            @Value("${app.ollama.embedding-dimensions}") int dimensions,
            @Value("${app.ollama.embedding-table}") String table,
            @Value("${app.ollama.temperature}") Double temperature,
            @Value("${app.ollama.num-ctx}") Integer numCtx,
            @Value("${app.ollama.min-score}") double minScore,
            @Value("${app.ollama.timeout}") Duration timeout) {

        ChatModel chat = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(chatModelName)
                .temperature(temperature)
                .numCtx(numCtx)
                .timeout(timeout)
                .build();

        StreamingChatModel streaming = OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(chatModelName)
                .temperature(temperature)
                .numCtx(numCtx)
                .timeout(timeout)
                .build();

        EmbeddingModel embedding = OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(embeddingModelName)
                .timeout(timeout)
                .build();

        return assemble("ollama", chatModelName, embeddingModelName,
                chat, streaming, embedding, table, dimensions, minScore);
    }

    // ---------------------------------------------------------------- OPENAI

    @Bean
    @ConditionalOnProperty(name = "app.openai.enabled", havingValue = "true")
    AiStack openaiStack(
            @Value("${app.openai.api-key}") String apiKey,
            @Value("${app.openai.chat-model}") String chatModelName,
            @Value("${app.openai.embedding-model}") String embeddingModelName,
            @Value("${app.openai.embedding-dimensions}") int dimensions,
            @Value("${app.openai.embedding-table}") String table,
            @Value("${app.openai.temperature}") Double temperature,
            @Value("${app.openai.min-score}") double minScore,
            @Value("${app.openai.timeout}") Duration timeout) {

        ChatModel chat = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(chatModelName)
                .temperature(temperature)
                .timeout(timeout)
                .build();

        StreamingChatModel streaming = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(chatModelName)
                .temperature(temperature)
                .timeout(timeout)
                .build();

        EmbeddingModel embedding = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .dimensions(dimensions)
                .timeout(timeout)
                .build();

        return assemble("openai", chatModelName, embeddingModelName,
                chat, streaming, embedding, table, dimensions, minScore);
    }

    // ---------------------------------------------------------------- COMMUN

    private AiStack assemble(String name,
                             String chatModelName,
                             String embeddingModelName,
                             ChatModel chat,
                             StreamingChatModel streaming,
                             EmbeddingModel embedding,
                             String table,
                             int dimensions,
                             double minScore) {

        EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
                .host(pgHost)
                .port(pgPort)
                .database(pgDatabase)
                .user(pgUser)
                .password(pgPassword)
                .table(table)
                .dimension(dimensions)
                .createTable(true)
                .build();

        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        EnterpriseAssistantService assistant = AiServices.builder(EnterpriseAssistantService.class)
                .chatModel(chat)
                .streamingChatModel(streaming)
                .contentRetriever(retriever)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(name + ":" + memoryId)      // historiques séparés par fournisseur
                        .maxMessages(10)
                        .chatMemoryStore(memoryStore)
                        .build())
                .build();

        return new AiStack(name, chatModelName, embeddingModelName,
                chat, streaming, embedding, store, assistant);
    }
}