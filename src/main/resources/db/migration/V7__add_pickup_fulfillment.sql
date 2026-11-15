ALTER TABLE checkout_orders
    ADD COLUMN fulfillment_mode VARCHAR(16) NOT NULL DEFAULT 'PICKUP';

ALTER TABLE checkout_orders
    ADD CONSTRAINT ck_checkout_order_fulfillment_mode
        CHECK (fulfillment_mode = 'PICKUP');
