ALTER TABLE checkout_orders
    DROP CONSTRAINT ck_checkout_order_status;

ALTER TABLE checkout_orders
    ADD CONSTRAINT ck_checkout_order_status
        CHECK (status IN ('PENDING_PAYMENT', 'ASYNC_PAYMENT_PENDING', 'PAID', 'EXPIRED', 'FAILED'));
