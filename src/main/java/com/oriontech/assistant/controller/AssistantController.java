package com.oriontech.assistant.controller;

import com.oriontech.assistant.service.EnterpriseAssistantService;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assistant")
public class AssistantController {

    private final EnterpriseAssistantService assistant;

    public record AssistantAnswer(String answer, List<String> sources) {}

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public AssistantAnswer chat(@RequestParam(defaultValue = "default") String sessionId,
                                @RequestBody String message) {
        Result<String> result = assistant.chat(sessionId, message);

        List<String> sources = result.sources() == null ? List.of() : result.sources().stream()
                .map(c -> c.textSegment().metadata().getString("file_name"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return new AssistantAnswer(result.content(), sources);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "default") String sessionId,
                             @RequestParam String q) {
        SseEmitter emitter = new SseEmitter(300_000L);

        assistant.stream(sessionId, q)
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
}
