package com.oriontech.assistant.service;

import dev.langchain4j.service.*;

public interface EnterpriseAssistantService {

    @SystemMessage("""
        Tu es un assistant d'entreprise. Réponds en français, de façon concise.
        Appuie-toi sur le contexte fourni quand il est pertinent. Si l'information
        n'y figure pas, dis-le clairement au lieu d'inventer.
        """)
    Result<String> chat(@MemoryId String sessionId, @UserMessage String message);

    @SystemMessage("""
        Tu es un assistant d'entreprise. Réponds en français, de façon concise.
        Appuie-toi sur le contexte fourni quand il est pertinent. Si l'information
        n'y figure pas, dis-le clairement au lieu d'inventer.
        """)
    TokenStream stream(@MemoryId String sessionId, @UserMessage String message);
}

