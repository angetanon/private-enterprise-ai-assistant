package com.oriontech.assistant.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresChatMemoryStoreTest {

    @Mock
    JdbcTemplate jdbc;

    @InjectMocks
    PostgresChatMemoryStore store;

    @Test
    void getMessages_returnsEmptyList_whenNoRowExists() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("alice"))).thenReturn(List.of());

        assertThat(store.getMessages("alice")).isEmpty();
    }

    @Test
    void getMessages_deserializesStoredJson() {
        String json = ChatMessageSerializer.messagesToJson(
                List.of(UserMessage.from("Bonjour"), AiMessage.from("Salut")));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("alice"))).thenReturn(List.of(json));

        List<ChatMessage> messages = store.getMessages("alice");

        assertThat(messages).hasSize(2);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo("Bonjour");
        assertThat(((AiMessage) messages.get(1)).text()).isEqualTo("Salut");
    }

    @Test
    void getMessages_convertsNonStringMemoryIdToString() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("42"))).thenReturn(List.of());

        assertThat(store.getMessages(42)).isEmpty();
    }

    @Test
    void updateMessages_upsertsSerializedMessages() {
        List<ChatMessage> messages = List.of(UserMessage.from("Bonjour"));

        store.updateMessages("alice", messages);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq("alice"), eq(ChatMessageSerializer.messagesToJson(messages)));
        assertThat(sql.getValue())
                .contains("INSERT INTO chat_memory")
                .contains("ON CONFLICT");
    }

    @Test
    void deleteMessages_deletesByMemoryId() {
        store.deleteMessages("alice");

        verify(jdbc).update(eq("DELETE FROM chat_memory WHERE memory_id = ?"), eq("alice"));
    }
}