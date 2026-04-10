CREATE TABLE products (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    slug           VARCHAR(255) UNIQUE NOT NULL,
    description    TEXT,
    type           VARCHAR(20) NOT NULL CHECK (type IN ('DRESS', 'ACCESSORY')),
    category_id    UUID REFERENCES categories(id) ON DELETE SET NULL,
    rental_price   DECIMAL(10,2),
    purchase_price DECIMAL(10,2),
    stock          INTEGER NOT NULL DEFAULT 0,
    sizes          TEXT,
    is_available   BOOLEAN DEFAULT TRUE,
    created_at     TIMESTAMP DEFAULT NOW(),
    updated_at     TIMESTAMP DEFAULT NOW()
);

CREATE TABLE product_images (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID REFERENCES products(id) ON DELETE CASCADE,
    url           VARCHAR(500) NOT NULL,
    display_order INTEGER DEFAULT 0,
    created_at    TIMESTAMP DEFAULT NOW()
);