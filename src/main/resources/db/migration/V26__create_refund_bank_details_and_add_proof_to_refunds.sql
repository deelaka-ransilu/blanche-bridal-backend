CREATE TABLE refund_bank_details (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
                                     account_holder_name VARCHAR(255) NOT NULL,
                                     account_number VARCHAR(50) NOT NULL,
                                     bank_name VARCHAR(100) NOT NULL,
                                     branch VARCHAR(100),
                                     submitted_at TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE refunds ADD COLUMN proof_image_url VARCHAR(500);