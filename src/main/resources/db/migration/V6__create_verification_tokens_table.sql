-- V6__create_verification_tokens_table.sql
CREATE TABLE verification_tokens (
                                     id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                     token      VARCHAR(64) UNIQUE NOT NULL,
                                     type       VARCHAR(20) NOT NULL CHECK (type IN ('EMAIL_VERIFY', 'PASSWORD_RESET')),
                                     expires_at TIMESTAMP NOT NULL,
                                     created_at TIMESTAMP DEFAULT NOW()
);