package com.oriontech.assistant.configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RagConfig {

    @Bean
    EmbeddingModel embeddingModel(
            @Value("${app.ollama.base-url}") String baseUrl,
            @Value("${app.rag.embedding-model}") String model,
            @Value("${app.rag.embedding-timeout:5m}") Duration timeout) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(timeout)
                .maxRetries(1) // un retry relance tout le lot et sature Ollama (CPU)
                .build();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(
            @Value("${app.pgvector.host}") String host,
            @Value("${app.pgvector.port}") int port,
            @Value("${app.pgvector.database}") String database,
            @Value("${app.pgvector.user}") String user,
            @Value("${app.pgvector.password}") String password,
            @Value("${app.rag.table}") String table,
            @Value("${app.rag.dimension}") int dimension,
            @Value("${app.rag.create-table}") boolean createTable) {
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table(table)
                .dimension(dimension)
                .createTable(createTable)
                .build();
    }

    @Bean
    ContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> store,
            EmbeddingModel embeddingModel,
            @Value("${app.rag.max-results}") int maxResults,
            @Value("${app.rag.min-score}") double minScore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }
}