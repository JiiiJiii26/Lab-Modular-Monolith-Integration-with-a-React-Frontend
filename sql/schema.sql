-- schema.sql
-- Creates inventory and orders tables and seeds initial inventory data

-- Inventory table
CREATE TABLE IF NOT EXISTS inventory (
    product_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    stock INT NOT NULL CHECK (stock >= 0)
);

-- Orders table
CREATE TABLE IF NOT EXISTS orders (
    order_id BIGSERIAL PRIMARY KEY,
    product_id TEXT NOT NULL REFERENCES inventory(product_id),
    quantity INT NOT NULL CHECK (quantity > 0),
    status TEXT NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed initial inventory data
INSERT INTO inventory (product_id, name, stock)
VALUES 
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
ON CONFLICT (product_id) DO UPDATE 
SET name = EXCLUDED.name,
    stock = EXCLUDED.stock;
