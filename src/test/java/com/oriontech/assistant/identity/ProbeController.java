package com.oriontech.assistant.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProbeController {

    @PostMapping("/api/mistral/chat")
    public String chat() {
        return "ok";
    }

    @PostMapping("/api/documents")
    public String upload() {
        return "ok";
    }

    @GetMapping("/other")
    public String other() {
        return "ok";
    }
}