-- KP Data Sync: Excel lifecycle metadata
-- Extends the existing messages table only. No data is deleted or recreated.

ALTER TABLE messages ADD COLUMN excel_status TEXT NOT NULL DEFAULT 'draft';
ALTER TABLE messages ADD COLUMN excel_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE messages ADD COLUMN excel_published_at TEXT;
ALTER TABLE messages ADD COLUMN excel_published_by TEXT;
