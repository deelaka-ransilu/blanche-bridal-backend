ALTER TABLE appointments DROP CONSTRAINT appointments_type_check;

ALTER TABLE appointments ADD CONSTRAINT appointments_type_check
    CHECK (type IN ('FITTING', 'RENTAL_PICKUP', 'PURCHASE', 'CUSTOM_CONSULTATION'));