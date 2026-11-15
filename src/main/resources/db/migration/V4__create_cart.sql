CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    painting_id UUID NOT NULL REFERENCES paintings(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_cart_items_user_painting UNIQUE (user_id, painting_id)
);

CREATE INDEX idx_cart_items_user_added ON cart_items(user_id, added_at DESC);
