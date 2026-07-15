CREATE TABLE reviews (
                         id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
                         product_id UUID      NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                         user_id    UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         rating     INTEGER   NOT NULL CHECK (rating BETWEEN 1 AND 5),
                         comment    TEXT,
                         status     VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING','APPROVED','REJECTED')),
                         created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reviews_product ON reviews(product_id);
CREATE INDEX idx_reviews_status ON reviews(status);
CREATE UNIQUE INDEX uq_reviews_product_user ON reviews(product_id, user_id);