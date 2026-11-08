package com.oriontech.assistant.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.memory",
        name = "cleanup-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class ChatMemoryCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryCleanupTask.class);

    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public ChatMemoryCleanupTask(JdbcTemplate jdbc,
                                 @Value("${app.memory.retention-days}") int retentionDays) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.memory.cleanup-cron}")
    public void purgeOldConversations() {
        int deleted = jdbc.update(
                "DELETE FROM chat_memory WHERE updated_at < now() - make_interval(days => ?)",
                retentionDays);

        log.info("Nettoyage de la mémoire : {} conversation(s) supprimée(s) (> {} jours)",
                deleted, retentionDays);
    }
}