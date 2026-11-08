package com.oriontech.assistant.controller;

import com.oriontech.assistant.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
public class DocumentController {

    private final static Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentIngestionService ingestionService;

    @PostMapping(consumes = "multipart/form-data")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        log.info("Uploading file: {}", file.getOriginalFilename());
        return Map.of(
                "file", file.getOriginalFilename(),
                "indexing", ingestionService.ingest(file));
    }
}