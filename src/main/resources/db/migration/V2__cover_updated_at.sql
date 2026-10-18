-- When the cover bytes last changed: versions coverUrl (?v=) and drives ETag/Last-Modified of the cover
ALTER TABLE audiobook ADD COLUMN cover_updated_at timestamptz;

UPDATE audiobook SET cover_updated_at = updated_at WHERE cover_key IS NOT NULL;
