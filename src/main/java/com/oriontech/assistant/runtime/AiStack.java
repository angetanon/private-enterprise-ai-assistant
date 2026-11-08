package com.oriontech.assistant.runtime;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

public record AiStack(
        String name,                 // "ollama" ou "mistral" (sert dans l'URL)
        String chatModelName,
        String embeddingModelName,
        ChatModel chatModel,
        StreamingChatModel streamingChatModel,
        EmbeddingModel embeddingModel,
        EmbeddingStore<TextSegment> embeddingStore,
        EnterpriseAssistant assistant) {
}