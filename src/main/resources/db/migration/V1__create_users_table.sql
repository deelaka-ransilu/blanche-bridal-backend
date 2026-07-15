CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
                       id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                       email         VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(255),
                       google_id     VARCHAR(255) UNIQUE,
                       role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN','EMPLOYEE','CUSTOMER')),
                       status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
                           CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','INACTIVE')),
                       first_name    VARCHAR(100),
                       last_name     VARCHAR(100),
                       phone         VARCHAR(50)  NOT NULL,
                       address       TEXT,
                       created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
                       updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Email unique only among non-INACTIVE users
CREATE UNIQUE INDEX uq_users_email_active
    ON users (email)
    WHERE status != 'INACTIVE';

-- Phone unique only among non-INACTIVE users
CREATE UNIQUE INDEX uq_users_phone_active
    ON users (phone)
    WHERE status != 'INACTIVE';