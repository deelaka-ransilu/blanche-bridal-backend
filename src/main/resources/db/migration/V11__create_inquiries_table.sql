CREATE TABLE inquiries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(20),
    subject     VARCHAR(255),
    message     TEXT NOT NULL,
    image_url   VARCHAR(500),
    status      VARCHAR(20) DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED')),
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);