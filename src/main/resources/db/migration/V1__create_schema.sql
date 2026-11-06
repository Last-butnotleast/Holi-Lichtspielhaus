CREATE TABLE showings (
    id BIGINT PRIMARY KEY,
    movie_title TEXT NOT NULL,
    starts_at TIMESTAMPTZ(6) NOT NULL
);

CREATE TABLE show_seats (
    showing_id BIGINT NOT NULL REFERENCES showings (id),
    seat_id BIGINT NOT NULL,
    hold_id UUID UNIQUE,
    hold_expires_at TIMESTAMPTZ(6),
    PRIMARY KEY (showing_id, seat_id),
    CHECK ((hold_id IS NULL) = (hold_expires_at IS NULL))
);
