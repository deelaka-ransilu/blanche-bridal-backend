CREATE TABLE gallery_images (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                url TEXT NOT NULL,
                                public_id VARCHAR(255),
                                caption TEXT,
                                display_order INTEGER NOT NULL DEFAULT 0,
                                is_active BOOLEAN NOT NULL DEFAULT TRUE,
                                created_at TIMESTAMP NOT NULL DEFAULT now()
);