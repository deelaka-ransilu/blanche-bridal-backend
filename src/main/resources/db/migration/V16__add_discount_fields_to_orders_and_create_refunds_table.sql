ALTER TABLE orders
    ADD COLUMN discount_type VARCHAR(20),
    ADD COLUMN discount_value NUMERIC(10,2),
    ADD COLUMN discount_reason VARCHAR(255);

CREATE TABLE refunds (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         order_id UUID NOT NULL REFERENCES orders(id),
                         amount NUMERIC(10,2) NOT NULL,
                         reason VARCHAR(255),
                         processed_by_admin_id UUID NOT NULL REFERENCES users(id),
                         created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_refunds_order_id ON refunds(order_id);