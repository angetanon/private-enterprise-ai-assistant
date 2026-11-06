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

### 2) Download the model

```powershell
docker exec -it ollama ollama pull llama3.2:3b
```

### 3) Run the Spring Boot application

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