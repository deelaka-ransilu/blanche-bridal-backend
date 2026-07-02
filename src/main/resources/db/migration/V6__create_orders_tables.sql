CREATE TABLE orders (
                        id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                        user_id            UUID          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
                        status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','CONFIRMED','PROCESSING','READY','COMPLETED','CANCELLED')),
                        total_amount       NUMERIC(10,2) NOT NULL,
                        notes              TEXT,
                        fulfillment_method VARCHAR(20),
                        delivery_address   TEXT,
                        customer_phone     VARCHAR(20),
                        order_mode         VARCHAR(10)   NOT NULL DEFAULT 'WEBSITE'
                            CHECK (order_mode IN ('WEBSITE','WALK_IN','WHATSAPP')),
                        created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
                        updated_at         TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_items (
                             id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                             order_id      UUID          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
                             product_id    UUID          REFERENCES products(id) ON DELETE SET NULL,
                             quantity      INTEGER       NOT NULL DEFAULT 1,
                             unit_price    NUMERIC(10,2) NOT NULL,
                             size          VARCHAR(20),
                             product_name  VARCHAR(255),
                             product_image VARCHAR(500)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);