package com.oriontech.assistant;

import com.oriontech.assistant.memory.ChatMemoryCleanupTask;
import com.oriontech.assistant.memory.PostgresChatMemoryStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DatabaseMigrationIntegrationTest {

    // needs to correspond to application.yml (embedding-table, embedding-dimensions)
    private static final String EMBEDDINGS_TABLE = "embeddings_ollama_bge_m3";
    private static final int DIMENSION = 1024;

    private static final DockerImageName IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void startContainer() {
        boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        if (System.getenv("CI") != null) {
            assertThat(dockerAvailable).as("Docker is required in CI").isTrue();
        } else {
            assumeTrue(dockerAvailable, "Docker is not available: test ignored");
        }

        postgres = new PostgreSQLContainer(IMAGE);
        postgres.start();

        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void resetDatabase() {
        // each test starts with an empty database
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
    }

    // ================================================================ MIGRATIONS

    @Test
    void migrate_onEmptyDatabase_createsAllTables() {
        flyway(false).migrate();

        assertThat(tables()).contains("chat_memory", EMBEDDINGS_TABLE, "flyway_schema_history");
        assertThat(appliedVersions()).contains("1", "2", "3");
    }

    @Test
    void migrate_isIdempotent() {
        Flyway flyway = flyway(false);
        flyway.migrate();

        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void migrate_onExistingDatabaseWithoutHistory_baselinesAndKeepsData() {
        // State of your dev database before Flyway: tables created by schema.sql and by LangChain4j
        jdbc.execute("CREATE EXTENSION vector");
        jdbc.execute("""
                CREATE TABLE chat_memory (
                    memory_id  VARCHAR(255) PRIMARY KEY,
                    messages   JSONB     NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT now()
                )""");
        jdbc.execute("""
                CREATE TABLE %s (
                    embedding_id UUID PRIMARY KEY,
                    embedding    vector(%d),
                    text         TEXT NULL,
                    metadata     JSON NULL
                )""".formatted(EMBEDDINGS_TABLE, DIMENSION));
        jdbc.update("INSERT INTO chat_memory (memory_id, messages) VALUES ('alice', '[]'::jsonb)");

        flyway(true).migrate();

        assertThat(appliedVersions()).contains("0", "1", "2", "3");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM chat_memory WHERE memory_id = 'alice'", Integer.class)).isEqualTo(1);
    }

    // ====================================================== CODE VS SCHEMA : MEMORY

    @Test
    void chatMemoryStore_roundTrip() {
        flyway(false).migrate();
        PostgresChatMemoryStore store = new PostgresChatMemoryStore(jdbc);

        store.updateMessages("alice", List.of(UserMessage.from("Hello"), AiMessage.from("Hello")));
        List<ChatMessage> loaded = store.getMessages("alice");

        assertThat(loaded).hasSize(2);
        assertThat(((UserMessage) loaded.get(0)).singleText()).isEqualTo("Hello");
        assertThat(((AiMessage) loaded.get(1)).text()).isEqualTo("Hello");

        // upsert: the second write replaces the first
        store.updateMessages("alice", List.of(UserMessage.from("New question")));
        assertThat(store.getMessages("alice")).hasSize(1);

        store.deleteMessages("alice");
        assertThat(store.getMessages("alice")).isEmpty();
    }

    @Test
    void cleanupTask_deletesOnlyOldConversations() {
        flyway(false).migrate();
        jdbc.update("""
                INSERT INTO chat_memory (memory_id, messages, updated_at)
                VALUES ('old', '[]'::jsonb, now() - interval '40 days')""");
        jdbc.update("""
                INSERT INTO chat_memory (memory_id, messages, updated_at)
                VALUES ('recent', '[]'::jsonb, now() - interval '2 days')""");

        new ChatMemoryCleanupTask(jdbc, 30).purgeOldConversations();

        assertThat(jdbc.queryForList("SELECT memory_id FROM chat_memory", String.class))
                .containsExactly("recent");
    }

    // ================================================= CODE VS SCHÉMA : EMBEDDINGS

    @Test
    void embeddingStore_addSearchAndRemoveByFileName() {
        flyway(false).migrate();
        EmbeddingStore<TextSegment> store = embeddingStore();

        store.add(unitVector(0), TextSegment.from("leave policy",
                Metadata.from("file_name", "leave.pdf")));
        store.add(unitVector(1), TextSegment.from("Expense report",
                    Metadata.from("file_name", "expense_report.pdf")));

        // high threshold: only the vector identical to the query is returned
        var strict = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(unitVector(0)).maxResults(5).minScore(0.9).build()).matches();
        assertThat(strict).hasSize(1);
        assertThat(strict.get(0).embedded().metadata().getString("file_name")).isEqualTo("leave.pdf");

        // metadata-based removal (used by DocumentIngestionService) works
        store.removeAll(metadataKey("file_name").isEqualTo("leave.pdf"));

        var remaining = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(unitVector(0)).maxResults(5).minScore(0.0).build()).matches();
        assertThat(remaining)
                .extracting(m -> m.embedded().metadata().getString("file_name"))
                .containsExactly("expense_report.pdf");
    }

    @Test
    void embeddingStore_rejectsVectorsOfWrongDimension() {
        flyway(false).migrate();
        EmbeddingStore<TextSegment> store = embeddingStore();

        assertThatThrownBy(() -> store.add(Embedding.from(new float[768]), TextSegment.from("text")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void embeddingStore_failsWhenSchemaIsMissing() {
        // no migration: createTable(false) should create nothing silently
        assertThatThrownBy(() -> embeddingStore().add(unitVector(0), TextSegment.from("text")))
                .isInstanceOf(RuntimeException.class);
    }

    // ===================================================================== UTILS

    private Flyway flyway(boolean baselineExistingDatabase) {
        FluentConfiguration config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (baselineExistingDatabase) {
            config.baselineOnMigrate(true).baselineVersion("0");
        }
        return config.load();
    }

    private EmbeddingStore<TextSegment> embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(postgres.getHost())
                .port(postgres.getFirstMappedPort())
                .database(postgres.getDatabaseName())
                .user(postgres.getUsername())
                .password(postgres.getPassword())
                .table(EMBEDDINGS_TABLE)
                .dimension(DIMENSION)
                .createTable(false)
                .build();
    }

    private List<String> tables() {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
    }

    private List<String> appliedVersions() {
        return jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
    }

    /** Zero vector except for one axis: two different axes yield a cosine similarity of 0. */
    private static Embedding unitVector(int axis) {
        float[] vector = new float[DIMENSION];
        vector[axis] = 1f;
        return Embedding.from(vector);
    }
}