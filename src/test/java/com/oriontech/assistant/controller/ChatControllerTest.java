package com.oriontech.assistant.controller;

import com.oriontech.assistant.ai.AiStack;
import com.oriontech.assistant.memory.PostgresChatMemoryStore;
import com.oriontech.assistant.service.EnterpriseAssistantService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    EnterpriseAssistantService ollamaAssistant;

    @Mock
    EnterpriseAssistantService mistralAssistant;

    @Mock
    PostgresChatMemoryStore memoryStore;


    private ChatController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new ChatController(List.of(ollamaStack(), mistralStack()), memoryStore);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ------------------------------------------------------------- logique

    @Test
    void chat_routesToOllama_andBuildsResponse() {
        Result<String> result = resultOf("Bonjour !");
        when(ollamaAssistant.chat("s1", "Salut")).thenReturn(result);

        Map<String, Object> body = controller.chat("ollama", "s1", "Salut");

        assertThat(body)
                .containsEntry("provider", "ollama")
                .containsEntry("chatModel", "llama3.2:3b")
                .containsEntry("embeddingModel", "bge-m3")
                .containsEntry("answer", "Bonjour !");
        assertThat(body.get("durationMs")).isInstanceOf(Long.class);
        verifyNoInteractions(mistralAssistant);
    }

    @Test
    void chat_routesTomistral() {
        Result<String> result = resultOf("Hello");
        when(mistralAssistant.chat("s1", "Salut")).thenReturn(result);

        Map<String, Object> body = controller.chat("mistral", "s1", "Salut");

        assertThat(body).containsEntry("provider", "mistral").containsEntry("answer", "Hello");
        verifyNoInteractions(ollamaAssistant);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chat_exposesTokenUsage() {
        Result<String> result = resultOf("Bonjour !");
        when(ollamaAssistant.chat("s1", "Salut")).thenReturn(result);

        Map<String, Object> body = controller.chat("ollama", "s1", "Salut");

        assertThat((Map<String, Object>) body.get("tokens"))
                .containsEntry("input", 10)
                .containsEntry("output", 20)
                .containsEntry("total", 30);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chat_exposesSourcesWithScoreAndAbbreviatedExtract() {
        Content content = mock(Content.class);
        when(content.textSegment()).thenReturn(
                TextSegment.from("x".repeat(300), Metadata.from("file_name", "conges.pdf")));
        when(content.metadata()).thenReturn(Map.of(ContentMetadata.SCORE, 0.82));

        Result<String> result = resultOf("25 jours", content);
        when(ollamaAssistant.chat("s1", "Combien de jours ?")).thenReturn(result);

        Map<String, Object> body = controller.chat("ollama", "s1", "Combien de jours ?");

        List<Map<String, Object>> sources = (List<Map<String, Object>>) body.get("sources");
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0)).containsEntry("file", "conges.pdf").containsEntry("score", 0.82);
        assertThat((String) sources.get(0).get("extract")).hasSize(151).endsWith("…");
    }

    @Test
    void chat_unknownProvider_throws404() {
        assertThatThrownBy(() -> controller.chat("openai", "s1", "Salut"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(404);
                    assertThat(e.getReason()).contains("openai");
                });
    }

    @Test
    void clearMemory_usesProviderPrefixedMemoryId() {
        Map<String, String> body = controller.clearMemory("mistral", "alice");

        verify(memoryStore).deleteMessages("mistral:alice");
        assertThat(body).containsEntry("status", "cleared").containsEntry("provider", "mistral");
    }

    @Test
    void stream_registersHandlersAndStartsTokenStream() {
        TokenStream tokenStream = mock(TokenStream.class, RETURNS_SELF);
        when(ollamaAssistant.stream("s1", "hello")).thenReturn(tokenStream);

        SseEmitter emitter = controller.stream("ollama", "s1", "hello");

        assertThat(emitter).isNotNull();
        verify(tokenStream).onPartialResponse(any());
        verify(tokenStream).onCompleteResponse(any());
        verify(tokenStream).onError(any());
        verify(tokenStream).start();
    }

    // ---------------------------------------------------------------- HTTP

    @Test
    void http_chat_returnsJson() throws Exception {
        Result<String> result = resultOf("Bonjour");
        when(ollamaAssistant.chat("s1", "Salut")).thenReturn(result);

        mvc.perform(post("/api/ollama/chat?sessionId=s1")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Salut"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.answer").value("Bonjour"));
    }

    @Test
    void http_unknownProvider_returns404() throws Exception {
        mvc.perform(post("/api/openai/chat")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Salut"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ utils

    private AiStack ollamaStack() {
        return new AiStack("ollama", "llama3.2:3b", "bge-m3", null, null, null, null, ollamaAssistant);
    }

    private AiStack mistralStack() {
        return new AiStack("mistral", "mistral-nemo:12b", "text-embedding-3-small", null, null, null, null, mistralAssistant);
    }

    @SuppressWarnings("unchecked")
    private Result<String> resultOf(String answer, Content... sources) {
        Result<String> result = mock(Result.class);
        when(result.content()).thenReturn(answer);
        when(result.tokenUsage()).thenReturn(new TokenUsage(10, 20));
        when(result.sources()).thenReturn(List.of(sources));
        return result;
    }
}