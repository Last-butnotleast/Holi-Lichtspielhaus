CREATE TABLE seat_hold_groups (
    hold_id UUID PRIMARY KEY,
    showing_id BIGINT NOT NULL REFERENCES showings (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ(6) NOT NULL,
    released_at TIMESTAMPTZ(6),
    booking_id UUID UNIQUE,
    CHECK (released_at IS NULL OR booking_id IS NULL)
);

CREATE INDEX seat_hold_groups_showing_idx ON seat_hold_groups (showing_id);

CREATE TABLE seat_hold_group_members (
    group_hold_id UUID NOT NULL REFERENCES seat_hold_groups (hold_id) ON DELETE CASCADE,
    seat_id BIGINT NOT NULL,
    member_hold_id UUID NOT NULL UNIQUE,
    PRIMARY KEY (group_hold_id, seat_id)
);
