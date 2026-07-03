-- V10__create_rentals_table.sql

CREATE TABLE rentals (
                         id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                         user_id        UUID          REFERENCES users(id) ON DELETE SET NULL,
                         product_id     UUID          REFERENCES products(id) ON DELETE SET NULL,
                         order_id       UUID          REFERENCES orders(id) ON DELETE SET NULL,
                         rental_start   DATE          NOT NULL,
                         rental_end     DATE          NOT NULL,
                         return_date    DATE,
                         status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'OVERDUE', 'RETURNED')),
                         deposit_amount NUMERIC(10,2),
                         balance_due    NUMERIC(10,2) NOT NULL DEFAULT 0,
                         notes          TEXT,
                         created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
                         updated_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rentals_user ON rentals(user_id);
CREATE INDEX idx_rentals_product ON rentals(product_id);
CREATE INDEX idx_rentals_status ON rentals(status);
CREATE INDEX idx_rentals_order ON rentals(order_id);