# Private Enterprise AI Assistant

Private enterprise AI assistant based on Spring Boot + LangChain4j + Ollama.

## Overview

This project exposes a simple REST API to send a message to a local LLM via Ollama.

- Endpoint: `POST /api/chat`
- Application port: `8080`
- Default Ollama: `http://localhost:11434`
- Default model: `llama3.2:3b`

## Prerequisites

- Java 25
- Docker Desktop (or a Compose-compatible Docker engine)
- Internet connection (for the first model download)

## Docker Compose

The project's `docker-compose.yml` file:

```yaml
services:
  ollama:
    image: ollama/ollama:latest
    container_name: ollama
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama
    environment:
      - OLLAMA_KEEP_ALIVE=30m
      - OLLAMA_NUM_PARALLEL=1
    restart: unless-stopped

volumes:
  ollama_data:
```

## Quick Start

### 1) Start Ollama

```powershell
docker compose up -d
```

### 2) Download the models

```powershell
docker exec -it ollama ollama pull llama3.2:3b
docker exec -it ollama ollama pull nomic-embed-text
docker exec -it ollama ollama pull mistral-nemo:12b
docker exec -it ollama ollama pull bge-m3
```

### 3) Check the models

```powershell
docker exec -it ollama ollama list
```
### Delete a model

```powershell
docker exec -it ollama ollama rm <model_name>
```

### 4) Run the Spring Boot application

```powershell
.\mvnw.cmd spring-boot:run
```

## Testing the API

From another terminal:

```powershell
curl -X POST "http://localhost:8080/api/chat" -H "Content-Type: text/plain" --data "Give me a summary of this quarter's sales."
```

## Configuration

The settings are in `src/main/resources/application.yaml`:

- `app.ollama.base-url`
- `app.ollama.model`
- `app.ollama.temperature`
- `app.ollama.timeout`
- `app.ollama.num-ctx`

## Tests

```powershell
.\mvnw.cmd test
```

# Knowledge Base (RAG)

RAG (Retrieval-Augmented Generation) queries for the enterprise assistant. The project uses an embedding model to index and search relevant documents.

```sql
-- Count the number of documents by file name
SELECT metadata->>'file_name' AS file, count(*) FROM document_embeddings GROUP BY 1;

-- Retrieve chat memory details
SELECT memory_id, jsonb_array_length(messages) AS nb_messages, updated_at FROM chat_memory;

--- Delete chat memory older than 30 days
DELETE FROM chat_memory WHERE updated_at < now() - interval '30 days'
```

# Project structure

```
private-enterprise-ai-assistant/
├── src/main/java/com/oriontech/assistant/
│   ├── PrivateEnterpriseAiAssistantApplication.java   (@SpringBootApplication, @EnableScheduling)
│   │
│   ├── assistant/                     ← Conversations
│   │   └── ChatController.java   (/api/{provider}/chat, /chat/stream, /memory)
│   │
│   ├── knowledge/                     ← Documents
│   │   └── DocumentController.java    (POST /api/documents)
│   │
│   ├── memory/                     
│   │   ├── PostgresChatMemoryStore.java   (/api/{provider}/chat, /chat/stream, /memory)
│   │   └── ChatMemoryCleanupTask.java
│   │
│   ├── rag/                           ← Indexation and retrieval
│   │   └── DocumentIngestionService.java
│   │
│   ├── runtime/                       ← AI Runtime (Ollama)
│   │   ├── AiStack.java               (record : models, store, assistant)
│   │   ├── AiStacksConfig.java        (one stack per models : llama, mistral)
│   │   └── EnterpriseAssistant.java   (interface AiServices : chat + stream)
│   │
│   ├── identity/
│   │   └── package-info.java          (empty for now)
│   ├── agent/
│   │   └── package-info.java
│   └── governance/
│       └── package-info.java
│
├── src/main/resources/
│   ├── application.yml
│   ├── prompts/
│   │   └── enterprise-assistant.txt   (strict system prompt)
│   └── db/migration/
│       ├── V1__extensions.sql
│       ├── V2__chat_memory.sql
│       └── V3__embeddings_bge_m3.sql
│
└── DocumentIngestionServiceTest.java
└── src/test/java/com/oriontech/assistant/
│   ├──  assistant/
│   │   ├── ChatControllerTest.java
│   ├──  memory/
│   │   ├── PostgresChatMemoryStoreTest.java
│   │   ├── ChatMemoryCleanupTaskTest.java
│   │   └── ChatMemoryCleanupTaskConditionTest.java
│   └── rag/
│       └── DocumentIngestionServiceTest.java
├── pom.xml
├── docker-compose.yml                 (ollama, pgvector)
├── .gitignore
└── README.md

```

#Swagger UI
localhost:8080/swagger-ui/index.html