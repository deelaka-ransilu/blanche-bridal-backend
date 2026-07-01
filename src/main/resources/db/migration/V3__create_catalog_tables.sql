CREATE TABLE categories (
                            id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                            name       VARCHAR(100) NOT NULL,
                            slug       VARCHAR(100) NOT NULL UNIQUE,
                            parent_id  UUID         REFERENCES categories(id) ON DELETE SET NULL,
                            is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
                            created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_categories_parent ON categories(parent_id);

CREATE TABLE products (
                          id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                          name           VARCHAR(255)   NOT NULL,
                          slug           VARCHAR(255)   NOT NULL UNIQUE,
                          description    TEXT,
                          type           VARCHAR(20)    NOT NULL CHECK (type IN ('DRESS','ACCESSORY')),
                          category_id    UUID           REFERENCES categories(id) ON DELETE SET NULL,
                          rental_price   NUMERIC(10,2),
                          purchase_price NUMERIC(10,2),
                          stock          INTEGER        NOT NULL DEFAULT 0,
                          sizes          TEXT,                       -- JSON array string, e.g. ["XS","S","M"]
                          is_available   BOOLEAN        NOT NULL DEFAULT TRUE,
                          is_active      BOOLEAN        NOT NULL DEFAULT TRUE,
                          created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
                          updated_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_type ON products(type);
CREATE INDEX idx_products_active ON products(is_active);

CREATE TABLE product_images (
                                id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                product_id    UUID         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                                url           VARCHAR(500) NOT NULL,
                                display_order INTEGER      NOT NULL DEFAULT 0,
                                is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
                                created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_images_product ON product_images(product_id);