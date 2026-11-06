INSERT INTO showings (id, movie_title, starts_at)
VALUES (1, 'Cinema Preview', '2027-01-15 19:30:00+01');

INSERT INTO show_seats (showing_id, seat_id)
SELECT 1, seat_number
FROM generate_series(1, 200) AS seats(seat_number);
