package com.oriontech.assistant.controller;

import com.oriontech.assistant.ai.AiStack;
import com.oriontech.assistant.memory.PostgresChatMemoryStore;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.service.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @PostMapping("/chat")
    public Map<String, Object> chat(@PathVariable String provider,
                                    @RequestParam(defaultValue = "default") String sessionId,
                                    @RequestBody String message) {
        AiStack stack = stack(provider);

        long start = System.nanoTime();
        Result<String> result = stack.assistant().chat(sessionId, message);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", stack.name());
        body.put("chatModel", stack.chatModelName());
        body.put("embeddingModel", stack.embeddingModelName());
        body.put("durationMs", durationMs);
        body.put("tokens", tokens(result.tokenUsage()));
        body.put("answer", result.content());
        body.put("sources", sources(result.sources()));
        return body;
    }

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
                    "Fournisseur inconnu ou désactivé : " + provider + " (disponibles : " + stacks.keySet() + ")");
        }
        return stack;
    }

    private Map<String, Object> tokens(TokenUsage usage) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (usage != null) {
            map.put("input", usage.inputTokenCount());
            map.put("output", usage.outputTokenCount());
            map.put("total", usage.totalTokenCount());
        }
        return map;
    }

    private List<Map<String, Object>> sources(List<Content> contents) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Content c : contents) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("file", c.textSegment().metadata().getString("file_name"));
            map.put("score", c.metadata().get(ContentMetadata.SCORE));
            map.put("extract", abbreviate(c.textSegment().text(), 150));
            list.add(map);
        }
        return list;
    }

    private String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}