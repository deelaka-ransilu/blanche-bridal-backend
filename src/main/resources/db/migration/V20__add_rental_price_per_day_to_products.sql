-- Adds an optional per-day rental rate. Existing products keep working off
-- the flat rental_price fee (see RentalServiceImpl.bookRental) until an
-- admin explicitly sets a per-day rate for a given dress.
ALTER TABLE products
    ADD COLUMN rental_price_per_day NUMERIC(10, 2);