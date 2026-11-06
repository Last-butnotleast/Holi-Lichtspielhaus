INSERT INTO showings (id, movie_title, starts_at)
VALUES (2, 'The Lantern House', '2027-01-15 16:00:00+01'),
       (3, 'Cinema Preview', '2027-01-16 19:30:00+01');

INSERT INTO show_seats (showing_id, seat_id)
SELECT showing.id, seat_number
FROM showings AS showing
CROSS JOIN generate_series(1, 200) AS seats(seat_number)
WHERE showing.id IN (2, 3);
