package com.oriontech.assistant.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PostgresChatMemoryStore implements ChatMemoryStore {

    private final JdbcTemplate jdbc;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<String> rows = jdbc.queryForList(
                "SELECT messages::text FROM chat_memory WHERE memory_id = ?",
                String.class,
                memoryId.toString());

        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        return ChatMessageDeserializer.messagesFromJson(rows.get(0));
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        jdbc.update("""
                INSERT INTO chat_memory (memory_id, messages, updated_at)
                VALUES (?, ?::jsonb, now())
                ON CONFLICT (memory_id)
                DO UPDATE SET messages = EXCLUDED.messages, updated_at = now()
                """,
                memoryId.toString(),
                ChatMessageSerializer.messagesToJson(messages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        jdbc.update("DELETE FROM chat_memory WHERE memory_id = ?", memoryId.toString());
    }
}