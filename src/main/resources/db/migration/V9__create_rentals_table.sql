CREATE TABLE rentals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    product_id      UUID REFERENCES products(id) ON DELETE SET NULL,
    order_id        UUID REFERENCES orders(id) ON DELETE SET NULL,
    rental_start    DATE NOT NULL,
    rental_end      DATE NOT NULL,
    return_date     DATE,
    status          VARCHAR(20) DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','OVERDUE','RETURNED')),
    deposit_amount  DECIMAL(10,2),
    balance_due     DECIMAL(10,2) DEFAULT 0,
    notes           TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);