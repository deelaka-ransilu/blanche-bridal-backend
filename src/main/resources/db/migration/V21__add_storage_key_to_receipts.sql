ALTER TABLE receipts ADD COLUMN storage_key VARCHAR(100);

-- Backfill existing rows with a random value so the NOT NULL constraint
-- below doesn't fail on rows created before this migration.
UPDATE receipts SET storage_key = gen_random_uuid()::text WHERE storage_key IS NULL;

ALTER TABLE receipts ALTER COLUMN storage_key SET NOT NULL;
ALTER TABLE receipts ADD CONSTRAINT uq_receipts_storage_key UNIQUE (storage_key);