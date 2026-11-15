CREATE TABLE checkout_orders (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL,
    type VARCHAR(16) NOT NULL,
    auction_id UUID,
    total_minor BIGINT NOT NULL CHECK (total_minor > 0),
    currency VARCHAR(3) NOT NULL,
    stripe_session_id VARCHAR(255) UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_checkout_order_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'EXPIRED', 'FAILED')),
    CONSTRAINT ck_checkout_order_type CHECK (type IN ('CART', 'AUCTION'))
);

CREATE INDEX idx_checkout_orders_user_created ON checkout_orders(user_id, created_at DESC);
CREATE INDEX idx_checkout_orders_status_expires ON checkout_orders(status, expires_at);
CREATE UNIQUE INDEX uk_checkout_orders_auction ON checkout_orders(auction_id)
    WHERE auction_id IS NOT NULL AND status IN ('PENDING_PAYMENT', 'PAID');

CREATE TABLE checkout_order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES checkout_orders(id) ON DELETE CASCADE,
    painting_id UUID NOT NULL,
    painting_title VARCHAR(200) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0)
);

CREATE INDEX idx_checkout_order_items_order ON checkout_order_items(order_id);

CREATE TABLE checkout_reservations (
    painting_id UUID PRIMARY KEY REFERENCES paintings(id) ON DELETE RESTRICT,
    order_id UUID NOT NULL REFERENCES checkout_orders(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_checkout_reservations_order ON checkout_reservations(order_id);
