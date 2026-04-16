CREATE TABLE orders (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED')),
    total_amount  DECIMAL(10,2) NOT NULL,
    notes         TEXT,
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);

CREATE TABLE order_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID REFERENCES orders(id) ON DELETE CASCADE,
    product_id    UUID REFERENCES products(id) ON DELETE SET NULL,
    quantity      INTEGER NOT NULL DEFAULT 1,
    unit_price    DECIMAL(10,2) NOT NULL,
    size          VARCHAR(20),
    product_name  VARCHAR(255),
    product_image VARCHAR(500)
);