package com.oriontech.assistant.service;


import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface EnterpriseAssistantService {

    @SystemMessage(fromResource = "prompts/enterprise-assistant.txt")
    Result<String> chat(@MemoryId String sessionId, @UserMessage String message);

    @SystemMessage(fromResource = "prompts/enterprise-assistant.txt")
    TokenStream stream(@MemoryId String sessionId, @UserMessage String message);
}

