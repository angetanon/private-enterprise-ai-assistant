package com.oriontech.assistant.service;

import com.oriontech.assistant.ai.AiStack;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentIngestionServiceTest {

    private static final String TEXT = "Politique de congés : 25 jours par an. ".repeat(80);

    private InMemoryEmbeddingStore<TextSegment> ollamaStore;
    private InMemoryEmbeddingStore<TextSegment> openaiStore;

    @BeforeEach
    void setUp() {
        ollamaStore = new InMemoryEmbeddingStore<>();
        openaiStore = new InMemoryEmbeddingStore<>();
    }

    @Test
    void ingest_indexesDocumentInEveryStack() throws Exception {
        DocumentIngestionService service = serviceWith(new FakeEmbeddingModel(), new FakeEmbeddingModel());

        Map<String, String> report = service.ingest(textFile("conges.txt"));

        assertThat(report).containsEntry("ollama", "indexed").containsEntry("openai", "indexed");
        assertThat(count(ollamaStore)).isGreaterThan(1);   // découpé en plusieurs morceaux
        assertThat(count(openaiStore)).isEqualTo(count(ollamaStore));
    }

    @Test
    void ingest_storesFileNameInMetadata() throws Exception {
        serviceWith(new FakeEmbeddingModel(), new FakeEmbeddingModel()).ingest(textFile("conges.txt"));

        var match = ollamaStore.search(searchAll()).matches().get(0);
        assertThat(match.embedded().metadata().getString("file_name")).isEqualTo("conges.txt");
    }

    @Test
    void ingest_sameFileTwice_doesNotCreateDuplicates() throws Exception {
        DocumentIngestionService service = serviceWith(new FakeEmbeddingModel(), new FakeEmbeddingModel());

        service.ingest(textFile("conges.txt"));
        int afterFirst = count(ollamaStore);
        service.ingest(textFile("conges.txt"));

        assertThat(count(ollamaStore)).isEqualTo(afterFirst);
    }

    @Test
    void ingest_oneProviderFails_otherStillIndexed() throws Exception {
        EmbeddingModel failing = new EmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                throw new IllegalStateException("quota exceeded");
            }
        };
        DocumentIngestionService service = serviceWith(new FakeEmbeddingModel(), failing);

        Map<String, String> report = service.ingest(textFile("conges.txt"));

        assertThat(report.get("ollama")).isEqualTo("indexed");
        assertThat(report.get("openai")).startsWith("failed").contains("quota exceeded");
        assertThat(count(ollamaStore)).isGreaterThan(0);
        assertThat(count(openaiStore)).isZero();
    }

    // ------------------------------------------------------------------ utils

    private DocumentIngestionService serviceWith(EmbeddingModel ollamaModel, EmbeddingModel openaiModel) {
        AiStack ollama = new AiStack("ollama", "llama3.2:3b", "bge-m3",
                null, null, ollamaModel, ollamaStore, null);
        AiStack openai = new AiStack("openai", "gpt-4o-mini", "text-embedding-3-small",
                null, null, openaiModel, openaiStore, null);
        DocumentIngestionService service = new DocumentIngestionService(List.of(ollama, openai));
        ReflectionTestUtils.setField(service, "chunkSize", 500);
        ReflectionTestUtils.setField(service, "chunkOverlap", 50);
        return service;
    }

    private MockMultipartFile textFile(String name) {
        return new MockMultipartFile("file", name, "text/plain", TEXT.getBytes(StandardCharsets.UTF_8));
    }

    private EmbeddingSearchRequest searchAll() {
        return EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[]{1f, 0f}))
                .maxResults(1000)
                .build();
    }

    private int count(EmbeddingStore<TextSegment> store) {
        return store.search(searchAll()).matches().size();
    }

    /** Embedding bidon : un vecteur constant par segment, aucun appel réseau. */
    private static class FakeEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            return Response.from(segments.stream()
                    .map(s -> Embedding.from(new float[]{1f, 0f}))
                    .toList());
        }
    }
}