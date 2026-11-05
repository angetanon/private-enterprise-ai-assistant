package com.oriontech.assistant.controller;

import com.oriontech.assistant.service.EnterpriseAssistantService;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantControllerTest {

    public static final String PATH = "/api/assistant";
    private EnterpriseAssistantService assistant;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        assistant = mock(EnterpriseAssistantService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AssistantController(assistant)).build();
    }

    @Test
    void chat_utiliseLaSessionParDefaut() throws Exception {
        when(assistant.chat("default", "Bonjour")).thenReturn(result("Salut !", "doc.txt"));

        mockMvc.perform(post("/api/assistant")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Bonjour"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Salut !"))
                .andExpect(jsonPath("$.sources[0]").value("doc.txt"));

        verify(assistant).chat("default", "Bonjour");
    }

    @Test
    void chat_transmetLeSessionIdFourni() throws Exception {
        when(assistant.chat("abc", "Question")).thenReturn(result("Réponse", "a.txt", "a.txt"));

        mockMvc.perform(post("/api/assistant")
                        .param("sessionId", "abc")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Question"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Réponse"))
                .andExpect(jsonPath("$.sources.length()").value(1));
    }

    private static Result<String> result(String answer, String... files) {
        List<Content> sources = Arrays.stream(files)
                .map(f -> (Content) Content.from(TextSegment.from("texte", Metadata.from("file_name", f))))
                .toList();
        return Result.<String>builder().content(answer).sources(sources).build();
    }

    @Test
    void stream_envoieLesTokensEnSse() throws Exception {
        TokenStream tokenStream = mock(TokenStream.class, Mockito.RETURNS_SELF);
        when(assistant.stream(eq("s1"), anyString())).thenReturn(tokenStream);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<String>> partial = ArgumentCaptor.forClass(Consumer.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ChatResponse>> complete = ArgumentCaptor.forClass(Consumer.class);

        Mockito.doAnswer(inv -> {
            verify(tokenStream).onPartialResponse(partial.capture());
            verify(tokenStream).onCompleteResponse(complete.capture());
            partial.getValue().accept("Bon");
            partial.getValue().accept("jour");
            complete.getValue().accept(null);
            return null;
        }).when(tokenStream).start();

        MvcResult result = mockMvc.perform(get(PATH + "/stream")
                        .param("sessionId", "s1")
                        .param("q", "Salut"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());

        assertThat(result.getResponse().getContentAsString())
                .contains("data:Bon")
                .contains("data:jour");
        verify(assistant).stream("s1", "Salut");
    }

    @Test
    void stream_sansParametreQ_retourne400() throws Exception {
        mockMvc.perform(get(PATH + "/stream"))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(assistant);
    }
}

