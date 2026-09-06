-- ============================================================================
-- KP Data Sync: D1 Migration 0002 - Safe Schema Reconciliation
--
-- Purpose:
--   Reconcile the already-created seven production tables with the committed
--   canonical schema without losing rows.
--
-- Safety:
--   * Does NOT create a clusters table.
--   * Copies every existing row into canonical replacement tables.
--   * Replaces tables only after all copies succeed.
--   * D1 runs migrations transactionally; a failed statement rolls the
--     migration back, so the original tables/data remain intact.
--   * Foreign-key checks are deferred for the table replacement operation.
--   * Canonical indexes are recreated after the replacement.
--
-- Important:
--   This migration intentionally uses explicit column lists rather than
--   SELECT * so column order changes cannot silently corrupt copied data.
-- ============================================================================

PRAGMA defer_foreign_keys = ON;

-- --------------------------------------------------------------------------
-- 1. Build canonical replacement tables.
-- --------------------------------------------------------------------------
CREATE TABLE __kp_new_schools (
    id TEXT PRIMARY KEY,
    school_name TEXT NOT NULL,
    udise_code TEXT UNIQUE,
    cluster_name TEXT,
    cluster_code TEXT,
    taluka TEXT,
    district TEXT,
    hm_name TEXT,
    hm_mobile TEXT,
    school_type TEXT,
    is_active INTEGER DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE __kp_new_users (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    mobile_number TEXT,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('Admin', 'Cluster_Head', 'School_HM', 'Teacher')),
    cluster_name TEXT,
    cluster_code TEXT,
    school_name TEXT,
    school_code TEXT,
    address TEXT,
    status TEXT NOT NULL DEFAULT 'Active' CHECK(status IN ('Active', 'Inactive')),
    fcm_token TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE __kp_new_groups (
    id TEXT PRIMARY KEY,
    group_name TEXT NOT NULL,
    group_type TEXT NOT NULL,
    created_by TEXT NOT NULL,
    cluster_code TEXT,
    description TEXT,
    is_active INTEGER DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES __kp_new_users(id) ON DELETE CASCADE
);

CREATE TABLE __kp_new_group_members (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    group_name TEXT,
    user_id TEXT NOT NULL,
    user_name TEXT,
    role_in_group TEXT,
    is_active INTEGER DEFAULT 1,
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES __kp_new_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES __kp_new_users(id) ON DELETE CASCADE
);

CREATE TABLE __kp_new_messages (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    group_name TEXT,
    sender_id TEXT NOT NULL,
    sender_name TEXT,
    content TEXT,
    media_url TEXT,
    message_type TEXT NOT NULL DEFAULT 'text',
    is_deleted INTEGER DEFAULT 0,
    is_read INTEGER DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES __kp_new_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES __kp_new_users(id) ON DELETE CASCADE
);

CREATE TABLE __kp_new_tasks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    task_type TEXT,
    template_url TEXT,
    target_scope TEXT,
    target_code TEXT,
    due_date DATETIME,
    created_by TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES __kp_new_users(id) ON DELETE CASCADE
);

CREATE TABLE __kp_new_reports (
    id TEXT PRIMARY KEY,
    task_id TEXT,
    school_code TEXT NOT NULL,
    school_name TEXT,
    submitted_by TEXT NOT NULL,
    file_name TEXT,
    r2_file_key TEXT,
    r2_file_url TEXT,
    remarks TEXT,
    status TEXT DEFAULT 'submitted',
    submitted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(task_id, school_code),
    FOREIGN KEY (task_id) REFERENCES __kp_new_tasks(id) ON DELETE SET NULL,
    FOREIGN KEY (submitted_by) REFERENCES __kp_new_users(id) ON DELETE CASCADE
);

-- --------------------------------------------------------------------------
-- 2. Copy existing data into the canonical tables.
-- --------------------------------------------------------------------------
INSERT INTO __kp_new_schools (
    id, school_name, udise_code, cluster_name, cluster_code, taluka, district,
    hm_name, hm_mobile, school_type, is_active, created_at, updated_at
)
SELECT
    id, school_name, udise_code, cluster_name, cluster_code, taluka, district,
    hm_name, hm_mobile, school_type, is_active, created_at, updated_at
FROM schools;

INSERT INTO __kp_new_users (
    id, name, email, mobile_number, password_hash, role, cluster_name,
    cluster_code, school_name, school_code, address, status, fcm_token,
    created_at, updated_at
)
SELECT
    id, name, email, mobile_number, password_hash, role, cluster_name,
    cluster_code, school_name, school_code, address, status, fcm_token,
    created_at, updated_at
FROM users;

INSERT INTO __kp_new_groups (
    id, group_name, group_type, created_by, cluster_code, description,
    is_active, created_at, updated_at
)
SELECT
    id, group_name, group_type, created_by, cluster_code, description,
    is_active, created_at, updated_at
FROM groups;

INSERT INTO __kp_new_group_members (
    id, group_id, group_name, user_id, user_name, role_in_group,
    is_active, joined_at
)
SELECT
    id, group_id, group_name, user_id, user_name, role_in_group,
    is_active, joined_at
FROM group_members;

INSERT INTO __kp_new_messages (
    id, group_id, group_name, sender_id, sender_name, content, media_url,
    message_type, is_deleted, is_read, created_at, updated_at
)
SELECT
    id, group_id, group_name, sender_id, sender_name, content, media_url,
    message_type, is_deleted, is_read, created_at, updated_at
FROM messages;

INSERT INTO __kp_new_tasks (
    id, title, description, task_type, template_url, target_scope,
    target_code, due_date, created_by, status, created_at
)
SELECT
    id, title, description, task_type, template_url, target_scope,
    target_code, due_date, created_by, status, created_at
FROM tasks;

INSERT INTO __kp_new_reports (
    id, task_id, school_code, school_name, submitted_by, file_name,
    r2_file_key, r2_file_url, remarks, status, submitted_at, updated_at
)
SELECT
    id, task_id, school_code, school_name, submitted_by, file_name,
    r2_file_key, r2_file_url, remarks, status, submitted_at, updated_at
FROM reports;

-- --------------------------------------------------------------------------
-- 3. Replace the old tables.
--    Child tables are dropped first so existing foreign keys cannot block the
--    replacement. The new tables reference only the __kp_new_* parents.
-- --------------------------------------------------------------------------
DROP TABLE reports;
DROP TABLE messages;
DROP TABLE group_members;
DROP TABLE tasks;
DROP TABLE groups;
DROP TABLE users;
DROP TABLE schools;

ALTER TABLE __kp_new_schools RENAME TO schools;
ALTER TABLE __kp_new_users RENAME TO users;
ALTER TABLE __kp_new_groups RENAME TO groups;
ALTER TABLE __kp_new_group_members RENAME TO group_members;
ALTER TABLE __kp_new_messages RENAME TO messages;
ALTER TABLE __kp_new_tasks RENAME TO tasks;
ALTER TABLE __kp_new_reports RENAME TO reports;

-- --------------------------------------------------------------------------
-- 4. Recreate canonical performance indexes.
-- --------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_schools_cluster_code ON schools(cluster_code);
CREATE INDEX IF NOT EXISTS idx_schools_udise_code ON schools(udise_code);
CREATE INDEX IF NOT EXISTS idx_schools_is_active ON schools(is_active);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_cluster_code ON users(cluster_code);
CREATE INDEX IF NOT EXISTS idx_users_school_code ON users(school_code);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

CREATE INDEX IF NOT EXISTS idx_groups_created_by ON groups(created_by);
CREATE INDEX IF NOT EXISTS idx_groups_cluster_code ON groups(cluster_code);
CREATE INDEX IF NOT EXISTS idx_groups_is_active ON groups(is_active);

CREATE INDEX IF NOT EXISTS idx_group_members_group_id ON group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user_id ON group_members(user_id);
CREATE INDEX IF NOT EXISTS idx_group_members_is_active ON group_members(is_active);

CREATE INDEX IF NOT EXISTS idx_messages_group_id ON messages(group_id);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at);

CREATE INDEX IF NOT EXISTS idx_tasks_target_code ON tasks(target_code);
CREATE INDEX IF NOT EXISTS idx_tasks_created_by ON tasks(created_by);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);

CREATE INDEX IF NOT EXISTS idx_reports_task_id ON reports(task_id);
CREATE INDEX IF NOT EXISTS idx_reports_school_code ON reports(school_code);
CREATE INDEX IF NOT EXISTS idx_reports_submitted_by ON reports(submitted_by);

-- --------------------------------------------------------------------------
-- 5. Final referential-integrity check.
-- --------------------------------------------------------------------------
PRAGMA foreign_key_check;
PRAGMA defer_foreign_keys = OFF;
