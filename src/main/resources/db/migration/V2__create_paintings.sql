CREATE TABLE paintings (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(10000) NOT NULL,
    medium VARCHAR(120) NOT NULL,
    width_cm NUMERIC(7, 2) NOT NULL,
    height_cm NUMERIC(7, 2) NOT NULL,
    artwork_year INTEGER,
    price_minor BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_paintings_status_updated ON paintings(status, updated_at DESC);
CREATE INDEX idx_paintings_price ON paintings(price_minor);
CREATE INDEX idx_paintings_year ON paintings(artwork_year);

CREATE TABLE painting_photos (
    id UUID PRIMARY KEY,
    painting_id UUID NOT NULL REFERENCES paintings(id) ON DELETE CASCADE,
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    uploaded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_painting_photos_object_key UNIQUE (object_key),
    CONSTRAINT uk_painting_photos_order UNIQUE (painting_id, sort_order)
);

CREATE INDEX idx_painting_photos_painting ON painting_photos(painting_id, sort_order);
