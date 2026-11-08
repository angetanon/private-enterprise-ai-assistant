package com.oriontech.assistant.rag;

import com.oriontech.assistant.runtime.AiStack;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final static Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final List<AiStack> stacks;
    private final DocumentParser parser = new ApacheTikaDocumentParser();
    @Value("${app.rag.chunk-size}")
    private int chunkSize;
    @Value("${app.rag.chunk-overlap}")
    private int chunkOverlap;

    public Map<String, String> ingest(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();

        Document document;
        try (InputStream in = file.getInputStream()) {
            document = parser.parse(in);
        }
        document.metadata().put("file_name", fileName);

        Map<String, String> report = new LinkedHashMap<>();
        for (AiStack stack : stacks) {
            try {
                stack.embeddingStore().removeAll(metadataKey("file_name").isEqualTo(fileName));

                EmbeddingStoreIngestor.builder()
                        .documentSplitter(DocumentSplitters.recursive(chunkSize, chunkOverlap))
                        .embeddingModel(stack.embeddingModel())
                        .embeddingStore(stack.embeddingStore())
                        .build()
                        .ingest(document);

                report.put(stack.name(), "indexed");
            } catch (Exception e) {
                report.put(stack.name(), "failed: " + e.getMessage());
            }
        }

        log.info("Uploaded file: {} , indexing report: {}", fileName, report);
        return report;
    }
}