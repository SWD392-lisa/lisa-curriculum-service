ALTER TABLE session_recordings ADD COLUMN IF NOT EXISTS storage_object_key VARCHAR(2048);
ALTER TABLE session_recordings ADD COLUMN IF NOT EXISTS podcast_id VARCHAR(128);
