ALTER TABLE products
    ADD CONSTRAINT chk_products_purchase_or_rental_only
        CHECK (
            purchase_price IS NULL
                OR (rental_price IS NULL AND rental_price_per_day IS NULL)
            );