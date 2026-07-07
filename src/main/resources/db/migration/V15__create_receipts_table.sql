CREATE TABLE receipts (
                          id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          order_id        UUID NOT NULL UNIQUE REFERENCES orders(id),
                          payment_id      UUID NOT NULL UNIQUE REFERENCES payments(id),
                          receipt_number  VARCHAR(50) NOT NULL UNIQUE,
                          pdf_url         VARCHAR(500),
                          issued_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_receipts_receipt_number ON receipts(receipt_number);