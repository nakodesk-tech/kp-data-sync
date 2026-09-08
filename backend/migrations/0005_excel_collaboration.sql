-- KP Data Sync: Excel collaborative file lifecycle metadata.
-- Actual workbook bytes remain in R2; D1 stores only lifecycle/version metadata on the chat message.
ALTER TABLE messages ADD COLUMN excel_status TEXT NOT NULL DEFAULT 'editable';
ALTER TABLE messages ADD COLUMN excel_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE messages ADD COLUMN excel_published_at TEXT;
ALTER TABLE messages ADD COLUMN excel_published_by TEXT;

CREATE INDEX IF NOT EXISTS idx_messages_excel_status ON messages(group_id, message_type, excel_status);
