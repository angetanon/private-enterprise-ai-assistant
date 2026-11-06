package com.oriontech.assistant.memory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatMemoryCleanupTaskConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ChatMemoryCleanupTask.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withPropertyValues(
                    "app.memory.retention-days=30",
                    "app.memory.cleanup-cron=0 0 3 * * *");

    @Test
    void taskIsCreated_whenEnabled() {
        runner.withPropertyValues("app.memory.cleanup-enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(ChatMemoryCleanupTask.class));
    }

    @Test
    void taskIsAbsent_whenDisabled() {
        runner.withPropertyValues("app.memory.cleanup-enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ChatMemoryCleanupTask.class));
    }

    @Test
    void taskIsAbsent_whenPropertyMissing() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(ChatMemoryCleanupTask.class));
    }
}