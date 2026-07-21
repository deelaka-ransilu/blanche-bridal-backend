-- V28__rental_fitting_split_payment.sql

-- Add RENTAL_FITTING as a new appointment type. RENTAL_PICKUP is kept in the
-- constraint (not deleted) so we don't have to touch historical rows that
-- predate this change beyond the one-time migration below.
ALTER TABLE appointments DROP CONSTRAINT appointments_type_check;
ALTER TABLE appointments ADD CONSTRAINT appointments_type_check
    CHECK (type IN ('FITTING', 'RENTAL_PICKUP', 'RENTAL_FITTING', 'PURCHASE', 'CUSTOM_CONSULTATION'));

-- Migrate existing RENTAL_PICKUP appointments that are linked to a rental
-- (i.e. created by the old bookRental() flow) to RENTAL_FITTING, since that's
-- what they conceptually always were going forward.
UPDATE appointments
SET type = 'RENTAL_FITTING'
WHERE type = 'RENTAL_PICKUP'
  AND id IN (SELECT appointment_id FROM rentals WHERE appointment_id IS NOT NULL);

-- Rental: split payment tracking.
-- order_id (existing FK) now represents the FIRST payment (50% rental fee,
-- collected at fitting-booking time). handover_order_id is the SECOND
-- payment (remaining 50% rental fee + security deposit), collected at
-- pickup/handover.
ALTER TABLE rentals ADD COLUMN rental_fee NUMERIC(10,2);
ALTER TABLE rentals ADD COLUMN handover_order_id UUID REFERENCES orders(id) ON DELETE SET NULL;
ALTER TABLE rentals ADD COLUMN security_deposit_amount NUMERIC(10,2);
ALTER TABLE rentals ADD COLUMN security_deposit_refunded_amount NUMERIC(10,2);
ALTER TABLE rentals ADD COLUMN damage_cost NUMERIC(10,2);
ALTER TABLE rentals ADD COLUMN late_fee_amount NUMERIC(10,2);
ALTER TABLE rentals ADD COLUMN amount_owed_by_customer NUMERIC(10,2);
ALTER TABLE rentals ADD COLUMN handover_confirmed_at TIMESTAMP;

CREATE INDEX idx_rentals_handover_order ON rentals(handover_order_id);

-- Backfill rental_fee from the existing deposit_amount column for any
-- pre-existing rows (old flow stored the full rental fee there).
UPDATE rentals SET rental_fee = deposit_amount WHERE rental_fee IS NULL;