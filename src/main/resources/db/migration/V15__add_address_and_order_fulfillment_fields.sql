-- Add address to users table
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS address TEXT;

-- Add fulfillment and order mode fields to orders table
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS fulfillment_method VARCHAR(20) DEFAULT 'PICKUP',
    ADD COLUMN IF NOT EXISTS delivery_address   TEXT,
    ADD COLUMN IF NOT EXISTS customer_phone     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS order_mode         VARCHAR(10) DEFAULT 'PURCHASE';