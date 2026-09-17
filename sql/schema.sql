-- schema.sql
-- Drop-and-recreate script for Lab 2 Modular Monolith Integration

DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS inventory CASCADE;

-- Inventory table
CREATE TABLE inventory (
    product_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    stock INT NOT NULL CHECK (stock >= 0)
);

-- Orders table
CREATE TABLE orders (
    order_id BIGSERIAL PRIMARY KEY,
    status TEXT NOT NULL CHECK (status IN ('CONFIRMED', 'REJECTED', 'CANCELLED')),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Order Items table
CREATE TABLE order_items (
    order_item_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id TEXT NOT NULL REFERENCES inventory(product_id),
    quantity INT NOT NULL CHECK (quantity > 0)
);

-- Notifications table
CREATE TABLE notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    message TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('ORDER_CONFIRMED', 'ORDER_REJECTED', 'ORDER_CANCELLED', 'LOW_STOCK')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed initial inventory data
INSERT INTO inventory (product_id, name, stock)
VALUES 
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0);
