-- Store the receipt PDF directly in Postgres instead of Cloudinary.
-- Avoids Cloudinary's "untrusted customer" delivery block on raw/PDF
-- assets for free-tier accounts without a verified payment method.

ALTER TABLE receipts ADD COLUMN pdf_data BYTEA;

-- storage_key / storage_version / pdf_url were Cloudinary-specific and are
-- no longer required going forward. Relax constraints rather than dropping
-- the columns outright, so existing rows/history aren't destroyed.
ALTER TABLE receipts ALTER COLUMN storage_key DROP NOT NULL;