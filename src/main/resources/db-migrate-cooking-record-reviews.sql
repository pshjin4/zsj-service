-- Run once on an existing database before deploying the cooking record review feature.
-- This migration preserves all existing cooking records and their notes.
ALTER TABLE cooking_records
  MODIFY COLUMN note TEXT,
  ADD COLUMN rating TINYINT NULL,
  ADD COLUMN photo_urls TEXT NULL;
