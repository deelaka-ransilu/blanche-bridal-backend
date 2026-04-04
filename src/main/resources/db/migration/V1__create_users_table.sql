CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
        id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        email         VARCHAR(255) UNIQUE NOT NULL,
        password_hash VARCHAR(255),
        google_id     VARCHAR(255) UNIQUE,
        role          VARCHAR(20) NOT NULL CHECK (role IN ('SUPERADMIN','ADMIN','EMPLOYEE','CUSTOMER')),
        first_name    VARCHAR(100),
        last_name     VARCHAR(100),
        phone         VARCHAR(20),
        is_active     BOOLEAN DEFAULT TRUE,
        created_at    TIMESTAMP DEFAULT NOW(),
        updated_at    TIMESTAMP DEFAULT NOW()
);