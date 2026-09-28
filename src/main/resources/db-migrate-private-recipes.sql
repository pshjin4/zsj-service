-- Run once on an existing database to make all existing recipes private.
-- This changes visibility only and preserves recipe and cooking record data.
UPDATE recipes SET is_public = 0 WHERE is_public IS NULL OR is_public <> 0;
