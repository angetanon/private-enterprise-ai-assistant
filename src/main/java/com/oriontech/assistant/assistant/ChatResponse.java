package com.oriontech.assistant.assistant;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Assistant chat response")
public record ChatResponse(
        @Schema(example = "mistral") String provider,
        @Schema(example = "mistral:7b") String chatModel,
        @Schema(example = "bge-m3") String embeddingModel,
        @Schema(description = "Time in milliseconds", example = "8421") long durationMs,
        TokenUsageInfo tokens,
        @Schema(example = "I cannot find this information in the available documents.") String answer,
        List<SourceInfo> sources) {

    public record TokenUsageInfo(Integer input, Integer output, Integer total) {
    }

    public record SourceInfo(
            @Schema(example = "test.pdf") String file,
            @Schema(description = "Similarity score", example = "0.82") Double score,
            @Schema(description = "Start of the retrieved excerpt") String extract) {
    }
}