CREATE TABLE IF NOT EXISTS chat_memory (
    memory_id  VARCHAR(255) PRIMARY KEY,
    messages   JSONB        NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);