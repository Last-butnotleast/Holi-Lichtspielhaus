CREATE TABLE seat_bookings (
    showing_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    booking_id UUID NOT NULL UNIQUE,
    hold_id UUID NOT NULL UNIQUE,
    confirmed_at TIMESTAMPTZ(6) NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    birth_date DATE NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(50),
    terms_accepted BOOLEAN NOT NULL CHECK (terms_accepted),
    privacy_accepted BOOLEAN NOT NULL CHECK (privacy_accepted),
    newsletter_opt_in BOOLEAN NOT NULL,
    PRIMARY KEY (showing_id, seat_id),
    FOREIGN KEY (showing_id, seat_id) REFERENCES show_seats (showing_id, seat_id)
);
