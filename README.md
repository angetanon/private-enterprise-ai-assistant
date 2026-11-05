# Private Enterprise AI Assistant

Assistant IA prive d'entreprise base sur Spring Boot + LangChain4j + Ollama.

## Apercu

Ce projet expose une API REST simple pour envoyer un message a un modele LLM local via Ollama.

- Endpoint: `POST /api/chat`
- Port applicatif: `8080`
- Ollama par defaut: `http://localhost:11434`
- Modele par defaut: `llama3.2:3b`

## Prerequis

- Java 25
- Docker Desktop (ou moteur Docker compatible Compose)
- Connexion internet (au premier telechargement du modele)

## Docker Compose

Le fichier `docker-compose.yml` du projet:

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

## Demarrage rapide

### 1) Demarrer Ollama

```powershell
docker compose up -d
```

### 2) Telecharger le modele

```powershell
docker exec -it ollama ollama pull llama3.2:3b
```

### 3) Lancer l'application Spring Boot

```powershell
.\mvnw.cmd spring-boot:run
```

## Tester l'API

Depuis un autre terminal:

```powershell
curl -X POST "http://localhost:8080/api/chat" -H "Content-Type: text/plain" --data "Donne-moi un resume des ventes du trimestre."
```

## Configuration

Les parametres sont dans `src/main/resources/application.yaml`:

- `app.ollama.base-url`
- `app.ollama.model`
- `app.ollama.temperature`
- `app.ollama.timeout`
- `app.ollama.num-ctx`
## Tests

```powershell
.\mvnw.cmd test
```

# Base de connaissances (RAG)
Requetes RAG (Retrieval-Augmented Generation) pour l'assistant d'entreprise. Le projet utilise un modele d'embedding pour indexer et rechercher des documents pertinents.
```sql
SELECT metadata->>'file_name' AS fichier, count(*) FROM document_embeddings GROUP BY 1;
``` 
