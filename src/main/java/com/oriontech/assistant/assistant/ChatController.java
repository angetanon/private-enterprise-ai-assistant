package com.oriontech.assistant.assistant;

import com.oriontech.assistant.memory.PostgresChatMemoryStore;
import com.oriontech.assistant.runtime.AiStack;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.service.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Tag(name = "Assistant", description = "Research chat into documents, by local model.")
@RestController
@RequestMapping("/api/{provider}")
public class ChatController {

    private final Map<String, AiStack> stacks;
    private final PostgresChatMemoryStore memoryStore;

    public ChatController(List<AiStack> stackList, PostgresChatMemoryStore memoryStore) {
        this.stacks = stackList.stream()
                .collect(Collectors.toMap(AiStack::name, Function.identity()));
        this.memoryStore = memoryStore;
    }

    @Operation(summary = "Ask a question (complete answer) to the chosen model with search in indexed documents.",
            description = "Question chosen model with search in indexed documents.")
    @ApiResponse(responseCode = "200", description = "Response, duration, tokens and sources")
    @ApiResponse(responseCode = "404", description = "Unknown or disabled model", content = @io.swagger.v3.oas.annotations.media.Content)
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content)
    @PostMapping("/chat")
    public ChatResponse chat(
            @Parameter(description = "Model provider", required = true,
                    schema = @Schema(allowableValues = {"ollama", "mistral"}))
            @PathVariable String provider,
            @Parameter(description = "Conversation identifier (separate memory per model)")
            @RequestParam(defaultValue = "default") String sessionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Message to send to the model", required = true,
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
            schema = @Schema(type = "string", example = "What is the leave policy ?")))
            @RequestBody String message) {
        AiStack stack = stack(provider);

        long start = System.nanoTime();
        Result<String> result = stack.assistant().chat(sessionId, message);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        return new ChatResponse(
                stack.name(),
                stack.chatModelName(),
                stack.embeddingModelName(),
                durationMs,
                tokens(result.tokenUsage()),
                result.content(),
                sources(result.sources()));
    }

    @Operation(summary = "Ask question (streaming SSE)",
            description = "Event stream, one token per event, then a final event with 'complete' when the answer is complete.")
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String provider,
                             @RequestParam(defaultValue = "default") String sessionId,
                             @RequestParam String q) {
        AiStack stack = stack(provider);
        SseEmitter emitter = new SseEmitter(300_000L);

        stack.assistant().stream(sessionId, q)
                .onPartialResponse(token -> {
                    try {
                        emitter.send(token);
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onCompleteResponse(response -> emitter.complete())
                .onError(emitter::completeWithError)
                .start();

        return emitter;
    }

    @Operation(summary = "Clear conversation memory for the chosen model")
    @DeleteMapping("/memory")
    public Map<String, String> clearMemory(@PathVariable String provider,
                                           @RequestParam String sessionId) {
        AiStack stack = stack(provider);
        memoryStore.deleteMessages(stack.name() + ":" + sessionId);
        return Map.of("status", "cleared", "provider", stack.name(), "sessionId", sessionId);
    }

    // ------------------------------------------------------------------ utils

    private AiStack stack(String provider) {
        AiStack stack = stacks.get(provider);
        if (stack == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Unknown provider or disabled : " + provider + " (available : " + stacks.keySet() + ")");
        }
        return stack;
    }

    private ChatResponse.TokenUsageInfo tokens(TokenUsage usage) {
        if (usage == null) {
            return new ChatResponse.TokenUsageInfo(null, null, null);
        }
        return new ChatResponse.TokenUsageInfo(
                usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount());
    }

    private List<ChatResponse.SourceInfo> sources(List<Content> contents) {
        return contents.stream()
                .map(c -> new ChatResponse.SourceInfo(
                        c.textSegment().metadata().getString("file_name"),
                        c.metadata().get(ContentMetadata.SCORE) instanceof Number n ? n.doubleValue() : null,
                        abbreviate(c.textSegment().text(), 150)))
                .toList();
    }

    private String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}