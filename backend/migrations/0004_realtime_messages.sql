-- KP Data Sync: Realtime group messaging extensions
-- Extends the existing messages table for durable realtime chat metadata.
-- No tables are dropped or recreated.

ALTER TABLE messages ADD COLUMN attachment_key TEXT;
ALTER TABLE messages ADD COLUMN file_name TEXT;
ALTER TABLE messages ADD COLUMN mime_type TEXT;
ALTER TABLE messages ADD COLUMN file_size INTEGER;
ALTER TABLE messages ADD COLUMN link_url TEXT;
ALTER TABLE messages ADD COLUMN client_message_id TEXT;

CREATE INDEX IF NOT EXISTS idx_messages_group_created_at ON messages(group_id, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_group_client_message ON messages(group_id, sender_id, client_message_id);
CREATE INDEX IF NOT EXISTS idx_messages_attachment_key ON messages(attachment_key);
