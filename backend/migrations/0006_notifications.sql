-- ============================================================================
-- KP Data Sync: D1 Migration 0006 - Notifications
-- Notification content is stored in D1; optional image/PDF bytes remain in R2.
-- This migration is additive and safe for the existing schema.
-- ============================================================================

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS notifications (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    scope_type TEXT NOT NULL,
    scope_id TEXT,
    publisher_id TEXT NOT NULL,
    publisher_name TEXT NOT NULL,
    publisher_role TEXT NOT NULL CHECK(publisher_role IN ('Admin', 'Cluster_Head')),
    status TEXT NOT NULL DEFAULT 'draft' CHECK(status IN ('draft', 'published', 'deleted')),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME,
    published_by TEXT,
    attachment_key TEXT,
    attachment_name TEXT,
    attachment_mime_type TEXT,
    attachment_size INTEGER,
    attachment_type TEXT CHECK(attachment_type IN ('image', 'pdf') OR attachment_type IS NULL),
    is_deleted INTEGER NOT NULL DEFAULT 0 CHECK(is_deleted IN (0, 1))
);

CREATE TABLE IF NOT EXISTS notification_reads (
    id TEXT PRIMARY KEY,
    notification_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    read_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(notification_id, user_id),
    FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notifications_status_published
    ON notifications(status, is_deleted, published_at);
CREATE INDEX IF NOT EXISTS idx_notifications_scope
    ON notifications(scope_type, scope_id, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_notifications_publisher
    ON notifications(publisher_id, created_at);
CREATE INDEX IF NOT EXISTS idx_notifications_attachment
    ON notifications(attachment_key);
CREATE INDEX IF NOT EXISTS idx_notification_reads_notification
    ON notification_reads(notification_id, user_id);
CREATE INDEX IF NOT EXISTS idx_notification_reads_user
    ON notification_reads(user_id, read_at);
