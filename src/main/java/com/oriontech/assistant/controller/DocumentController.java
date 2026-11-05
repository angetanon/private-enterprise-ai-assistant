package com.oriontech.assistant.controller;

import com.oriontech.assistant.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    @PostMapping(consumes = "multipart/form-data")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        ingestionService.ingest(file);
        return Map.of("status", "indexed", "file", file.getOriginalFilename());
    }
}