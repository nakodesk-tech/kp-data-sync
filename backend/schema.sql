-- ============================================================================
-- KP Data Sync: Cloudflare D1 (SQLite) Initial Schema
-- Supports Role-Based Access Control (RBAC) and School Data Management
-- Roles: Admin, Cluster_Head, School_HM, Teacher
-- ============================================================================

PRAGMA foreign_keys = ON;

-- 1. CLUSTERS TABLE
CREATE TABLE IF NOT EXISTS clusters (
    id TEXT PRIMARY KEY,
    cluster_name TEXT NOT NULL UNIQUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 2. SCHOOLS TABLE
CREATE TABLE IF NOT EXISTS schools (
    id TEXT PRIMARY KEY,
    school_name TEXT NOT NULL,
    cluster_id TEXT NOT NULL,
    udise_code TEXT NOT NULL UNIQUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE
);

-- 3. USERS TABLE
-- Roles: Admin, Cluster_Head, School_HM, Teacher
-- Status: active, inactive, pending
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('Admin', 'Cluster_Head', 'School_HM', 'Teacher')),
    cluster_id TEXT,
    school_id TEXT,
    status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active', 'inactive', 'pending')),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE SET NULL,
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE SET NULL
);

-- 4. GROUPS TABLE
-- Scope: all, cluster, school, administrative, etc.
CREATE TABLE IF NOT EXISTS groups (
    id TEXT PRIMARY KEY,
    group_name TEXT NOT NULL,
    created_by TEXT NOT NULL,
    scope TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE
);

-- 5. GROUP MEMBERS TABLE
CREATE TABLE IF NOT EXISTS group_members (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    added_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(group_id, user_id)
);

-- 6. REPORTS TABLE
-- r2_file_url stores the Cloudflare R2 object key or signed/public URL
CREATE TABLE IF NOT EXISTS reports (
    id TEXT PRIMARY KEY,
    school_id TEXT NOT NULL,
    submitted_by TEXT NOT NULL,
    r2_file_url TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    FOREIGN KEY (submitted_by) REFERENCES users(id) ON DELETE CASCADE
);

-- ============================================================================
-- PERFORMANCE INDEXES
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_cluster_id ON users(cluster_id);
CREATE INDEX IF NOT EXISTS idx_users_school_id ON users(school_id);
CREATE INDEX IF NOT EXISTS idx_schools_cluster_id ON schools(cluster_id);
CREATE INDEX IF NOT EXISTS idx_schools_udise_code ON schools(udise_code);
CREATE INDEX IF NOT EXISTS idx_groups_created_by ON groups(created_by);
CREATE INDEX IF NOT EXISTS idx_group_members_group_id ON group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user_id ON group_members(user_id);
CREATE INDEX IF NOT EXISTS idx_reports_school_id ON reports(school_id);
CREATE INDEX IF NOT EXISTS idx_reports_submitted_by ON reports(submitted_by);

-- ============================================================================
-- SEED DATA (For Immediate Development & Testing)
-- Password for all default test accounts: "Password@123"
-- Stored as PBKDF2-HMAC-SHA256 hash (100,000 iterations):
-- format: <salt_hex>:<hash_hex>
-- ============================================================================

-- Clusters
INSERT OR IGNORE INTO clusters (id, cluster_name) VALUES 
('cluster-01', 'North Cluster 01'),
('cluster-02', 'South Cluster 02');

-- Schools
INSERT OR IGNORE INTO schools (id, school_name, cluster_id, udise_code) VALUES 
('school-01', 'Government Model High School North', 'cluster-01', '27010100101'),
('school-02', 'Government Primary School South', 'cluster-02', '27010200202');

-- Users for all 4 roles (Password@123)
-- PBKDF2 hash of "Password@123" with salt "0123456789abcdef0123456789abcdef" (100,000 iterations SHA-256):
-- Salt: 0123456789abcdef0123456789abcdef
-- Hash: b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f
INSERT OR IGNORE INTO users (id, name, email, password_hash, role, cluster_id, school_id, status) VALUES 
('user-admin-01', 'System App Admin', 'admin@kpdatasync.com', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'Admin', NULL, NULL, 'active'),
('user-cluster-01', 'Cluster Officer North', 'clusterhead@kpdatasync.com', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'Cluster_Head', 'cluster-01', NULL, 'active'),
('user-hm-01', 'Headmaster School 01', 'schoolhm@kpdatasync.com', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'School_HM', 'cluster-01', 'school-01', 'active'),
('user-teacher-01', 'Test Teacher 01', 'teacher@kpdatasync.com', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'Teacher', 'cluster-01', 'school-01', 'active');

-- Sample Initial Group
INSERT OR IGNORE INTO groups (id, group_name, created_by, scope) VALUES
('group-01', 'North Cluster Academic Coordination', 'user-admin-01', 'cluster');

-- Sample Group Members
INSERT OR IGNORE INTO group_members (id, group_id, user_id) VALUES
('gm-01', 'group-01', 'user-admin-01'),
('gm-02', 'group-01', 'user-cluster-01'),
('gm-03', 'group-01', 'user-hm-01'),
('gm-04', 'group-01', 'user-teacher-01');
