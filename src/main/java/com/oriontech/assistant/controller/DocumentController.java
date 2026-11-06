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
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return Map.of(
                "file", file.getOriginalFilename(),
                "indexing", ingestionService.ingest(file));
    }
}