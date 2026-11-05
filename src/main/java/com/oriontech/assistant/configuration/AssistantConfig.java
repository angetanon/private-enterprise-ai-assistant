package com.oriontech.assistant.configuration;

import com.oriontech.assistant.service.EnterpriseAssistantService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistantConfig {

    @Bean
    EnterpriseAssistantService enterpriseAssistantService(ChatModel chatModel,
                                                   StreamingChatModel streamingChatModel,
                                                   ContentRetriever contentRetriever) {
        return AiServices.builder(EnterpriseAssistantService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .contentRetriever(contentRetriever)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(10)
                        .build())
                .build();
    }
}