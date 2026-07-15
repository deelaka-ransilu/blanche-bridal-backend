ALTER TABLE rentals
    ADD CONSTRAINT uq_rentals_order_id UNIQUE (order_id);