CREATE TABLE customer_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    admin_notes     TEXT,
    design_image_urls TEXT[] DEFAULT '{}',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);