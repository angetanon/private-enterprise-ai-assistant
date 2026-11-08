CREATE TABLE IF NOT EXISTS chat_memory (
    memory_id  VARCHAR(255) PRIMARY KEY,
    messages   JSONB     NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
    );

-- sert à la purge planifiée
CREATE INDEX IF NOT EXISTS idx_chat_memory_updated_at ON chat_memory (updated_at);