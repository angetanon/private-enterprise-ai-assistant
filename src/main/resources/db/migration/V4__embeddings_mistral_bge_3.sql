CREATE TABLE IF NOT EXISTS embeddings_mistral_bge_m3 (
                                                 embedding_id UUID PRIMARY KEY,
                                                 embedding    vector(1024),
                                                 text         TEXT NULL,
                                                 metadata     JSON NULL
);

CREATE INDEX IF NOT EXISTS idx_embeddings_mistral_bge_m3_hnsw
    ON embeddings_mistral_bge_m3 USING hnsw (embedding vector_cosine_ops);