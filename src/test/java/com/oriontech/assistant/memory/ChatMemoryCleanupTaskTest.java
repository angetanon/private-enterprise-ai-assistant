package com.oriontech.assistant.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMemoryCleanupTaskTest {

    @Mock
    JdbcTemplate jdbc;

    @Test
    void purge_deletesConversationsOlderThanRetention() {
        ChatMemoryCleanupTask task = new ChatMemoryCleanupTask(jdbc, 30);

        task.purgeOldConversations();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(30));
        assertThat(sql.getValue())
                .contains("DELETE FROM chat_memory")
                .contains("updated_at");
    }

    @Test
    void purge_usesConfiguredRetention() {
        new ChatMemoryCleanupTask(jdbc, 7).purgeOldConversations();

        verify(jdbc).update(org.mockito.ArgumentMatchers.anyString(), eq(7));
    }
}