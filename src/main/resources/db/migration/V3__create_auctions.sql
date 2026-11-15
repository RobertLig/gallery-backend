CREATE TABLE auctions (
    id UUID PRIMARY KEY,
    painting_id UUID NOT NULL REFERENCES paintings(id) ON DELETE CASCADE,
    starting_price_minor BIGINT NOT NULL CHECK (starting_price_minor > 0),
    started_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_auctions_end_after_start CHECK (ends_at > started_at)
);

CREATE INDEX idx_auctions_painting_ends ON auctions(painting_id, ends_at DESC);
CREATE INDEX idx_auctions_active_ends ON auctions(ends_at);

CREATE TABLE bids (
    id UUID PRIMARY KEY,
    auction_id UUID NOT NULL REFERENCES auctions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    placed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_bids_auction_price ON bids(auction_id, amount_minor DESC);
CREATE INDEX idx_bids_auction_placed ON bids(auction_id, placed_at DESC);
