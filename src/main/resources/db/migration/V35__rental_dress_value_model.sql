-- V35__rental_dress_value_model.sql
-- Rewrites the rental deposit model from rentalFee-based to dressValue-based.
-- No backward-compat handling for existing rental rows — old data is not
-- being migrated/preserved (per project decision).

ALTER TABLE rentals ADD COLUMN booking_path VARCHAR(20) NOT NULL DEFAULT 'ADVANCE'
    CHECK (booking_path IN ('ADVANCE', 'SAME_DAY'));
ALTER TABLE rentals ALTER COLUMN booking_path DROP DEFAULT;

ALTER TABLE rentals ADD COLUMN dress_value NUMERIC(10,2);
ALTER TABLE rentals ADD COLUMN refund_amount NUMERIC(10,2);

ALTER TABLE rentals DROP COLUMN IF EXISTS deposit_amount;
ALTER TABLE rentals DROP COLUMN IF EXISTS security_deposit_amount;
ALTER TABLE rentals DROP COLUMN IF EXISTS security_deposit_refunded_amount;
ALTER TABLE rentals DROP COLUMN IF EXISTS balance_due;

-- dress_value must be populated before a rental can be booked — comes from
-- products.dress_value, checked in RentalServiceImpl going forward.