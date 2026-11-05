package com.oriontech.assistant.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final int BATCH_SIZE = 8;

    private final EmbeddingStore<TextSegment> store;
    private final EmbeddingModel embeddingModel;
    private final DocumentParser parser = new ApacheTikaDocumentParser();
    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentIngestionService(EmbeddingStore<TextSegment> store,
                                    EmbeddingModel embeddingModel,
                                    @Value("${app.rag.chunk-size}") int chunkSize,
                                    @Value("${app.rag.chunk-overlap}") int chunkOverlap) {
        this.store = store;
        this.embeddingModel = embeddingModel;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public void ingest(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();

        // évite les doublons si le même fichier est renvoyé
        store.removeAll(metadataKey("file_name").isEqualTo(name));

        try (InputStream in = file.getInputStream()) {
            Document document = parser.parse(in);
            document.metadata().put("file_name", name);

            List<TextSegment> segments = DocumentSplitters.recursive(chunkSize, chunkOverlap).split(document);

            // Embedding par petits lots : un seul appel pour tout le document dépasse le timeout sur CPU
            for (int i = 0; i < segments.size(); i += BATCH_SIZE) {
                List<TextSegment> batch = segments.subList(i, Math.min(i + BATCH_SIZE, segments.size()));
                List<Embedding> embeddings = embeddingModel.embedAll(batch).content();
                store.addAll(embeddings, batch);
                log.info("Document '{}' : {}/{} segments indexés", name, i + batch.size(), segments.size());
            }
        }
    }
}