ALTER TABLE custom_design_requests
    ADD COLUMN first_payment_order_id UUID REFERENCES orders(id) ON DELETE SET NULL;

ALTER TABLE custom_design_requests
    ADD COLUMN second_payment_order_id UUID REFERENCES orders(id) ON DELETE SET NULL;

CREATE INDEX idx_custom_design_requests_first_payment_order ON custom_design_requests(first_payment_order_id);
CREATE INDEX idx_custom_design_requests_second_payment_order ON custom_design_requests(second_payment_order_id);