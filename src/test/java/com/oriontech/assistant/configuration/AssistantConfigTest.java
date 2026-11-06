package com.oriontech.assistant.configuration;

import com.oriontech.assistant.service.EnterpriseAssistantService;
import com.oriontech.assistant.service.PostgresChatMemoryStore;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AssistantConfigTest {

    @Test
    void enterpriseAssistantService_estConstruit() {
        EnterpriseAssistantService service = new AssistantConfig().enterpriseAssistantService(
                mock(ChatModel.class),
                mock(StreamingChatModel.class),
                mock(ContentRetriever.class),
                mock(PostgresChatMemoryStore.class));

        assertThat(service).isNotNull();
    }
}

