ALTER TABLE categories
    ADD COLUMN type VARCHAR(20);

-- Test data — backfill directly by known slug rather than a careful
-- data-preserving migration.
UPDATE categories SET type = 'DRESS'     WHERE slug IN ('bridal-gowns', 'evening-gowns', 'cocktail-dresses', 'party-dresses');
UPDATE categories SET type = 'ACCESSORY' WHERE slug IN ('veils', 'tiaras-headpieces', 'jewellery', 'shoes');

-- Anything not covered above (future/unknown categories created before this
-- migration ran) — default to ACCESSORY rather than leaving type NULL and
-- failing the NOT NULL step below.
UPDATE categories SET type = 'ACCESSORY' WHERE type IS NULL;

ALTER TABLE categories
    ALTER COLUMN type SET NOT NULL;

ALTER TABLE categories
    ADD CONSTRAINT chk_categories_type CHECK (type IN ('DRESS', 'ACCESSORY'));