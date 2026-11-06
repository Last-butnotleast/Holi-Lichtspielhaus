ALTER TABLE showings
    ADD COLUMN revision INTEGER NOT NULL DEFAULT 1 CHECK (revision > 0);
