-- KP Data Sync: Group creation extensions
-- Adds explicit scope and R2 photo reference without changing the existing groups model.

ALTER TABLE groups ADD COLUMN scope_type TEXT NOT NULL DEFAULT 'cluster';
ALTER TABLE groups ADD COLUMN school_code TEXT;
ALTER TABLE groups ADD COLUMN photo_key TEXT;

CREATE INDEX IF NOT EXISTS idx_groups_scope_type ON groups(scope_type);
CREATE INDEX IF NOT EXISTS idx_groups_school_code ON groups(school_code);

-- Existing rows remain valid. New API-created groups use:
-- scope_type: system | cluster | school
-- photo_key: R2 object key for the optional group avatar.
