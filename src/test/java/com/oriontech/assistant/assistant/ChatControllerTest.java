package com.oriontech.assistant.assistant;

import com.oriontech.assistant.identity.CurrentUser;
import com.oriontech.assistant.identity.CurrentUserProvider;
import com.oriontech.assistant.runtime.AiStack;
import com.oriontech.assistant.memory.PostgresChatMemoryStore;
import com.oriontech.assistant.runtime.EnterpriseAssistant;
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
import java.util.Set;

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
    EnterpriseAssistant ollamaAssistant;

    @Mock
    EnterpriseAssistant mistralAssistant;

    @Mock
    PostgresChatMemoryStore memoryStore;

    @Mock
    CurrentUserProvider currentUser;

    private static final String USER_ID = "user1";
    private ChatController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.require())
                .thenReturn(new CurrentUser(USER_ID, "alice", Set.of("user")));
        controller = new ChatController(List.of(ollamaStack(), mistralStack()), memoryStore, currentUser);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ------------------------------------------------------------- logique

    @Test
    void chat_routesToOllama_andBuildsResponse() {
        Result<String> result = resultOf("Bonjour !");
        when(ollamaAssistant.chat(memoryId("s1"), "Salut")).thenReturn(result);

        ChatResponse body = controller.chat("ollama", "s1", "Salut");

        assertThat(body)
                .as("provider", "ollama")
                .as("chatModel", "llama3.2:3b")
                .as("embeddingModel", "bge-m3")
                .as("answer", "Bonjour !");
        assertThat(body.durationMs()).isInstanceOf(Long.class);
        verifyNoInteractions(mistralAssistant);
    }

    @Test
    void chat_routesTomistral() {
        Result<String> result = resultOf("Hello");
        when(mistralAssistant.chat(memoryId("s1"), "Salut")).thenReturn(result);

        ChatResponse body = controller.chat("mistral", "s1", "Salut");

        assertThat(body).as("provider", "mistral").as("answer", "Hello");
        verifyNoInteractions(ollamaAssistant);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chat_exposesTokenUsage() {
        Result<String> result = resultOf("Bonjour !");
        when(ollamaAssistant.chat(memoryId("s1"), "Salut")).thenReturn(result);

        ChatResponse body = controller.chat("ollama", "s1", "Salut");

        assertThat(body.tokens().input().equals(10));
        assertThat(body.tokens().output().equals(20));
        assertThat(body.tokens().total().equals(30));
        /*

        assertThat(ChatResponse) body.tokens().input().equals("10");
                .containsEntry("input", 10)
                .containsEntry("output", 20)
                .containsEntry("total", 30);*/
    }

    @Test
    @SuppressWarnings("unchecked")
    void chat_exposesSourcesWithScoreAndAbbreviatedExtract() {
        Content content = mock(Content.class);
        when(content.textSegment()).thenReturn(
                TextSegment.from("x".repeat(300), Metadata.from("file_name", "conges.pdf")));
        when(content.metadata()).thenReturn(Map.of(ContentMetadata.SCORE, 0.82));

        Result<String> result = resultOf("25 jours", content);
        when(ollamaAssistant.chat(memoryId("s1"), "Combien de jours ?")).thenReturn(result);

        ChatResponse body = controller.chat("ollama", "s1", "Combien de jours ?");

        List<ChatResponse.SourceInfo> sources = body.sources();
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().extract()).hasSize(151).endsWith("…");
        assertThat(sources.getFirst().score().equals(0.82));
        assertThat(sources.getFirst().file().equals("conges.pdf"));
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

        verify(memoryStore).deleteMessages("mistral:" + memoryId("alice")); // mistral:user1:alice
        assertThat(body).containsEntry("status", "cleared").containsEntry("provider", "mistral");
    }

    @Test
    void stream_registersHandlersAndStartsTokenStream() {
        TokenStream tokenStream = mock(TokenStream.class, RETURNS_SELF);
        when(ollamaAssistant.stream(memoryId("s1"), "hello")).thenReturn(tokenStream);

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
        when(ollamaAssistant.chat(memoryId("s1"), "Salut")).thenReturn(result);

        mvc.perform(post("/api/ollama/chat?conversationId=s1")
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

    /**
     * Returns the memory ID for a given conversation ID.
     * @param conversationId
     * @return
     */
    private static String memoryId(String conversationId) {
        return USER_ID + ":" + conversationId;
    }
}