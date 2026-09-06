-- ============================================================================
-- KP Data Sync: Development & Testing Seed Data
-- ============================================================================
-- WARNING: DO NOT RUN THIS IN PRODUCTION!
-- This file contains mock development fixtures and well-known test credentials.
-- Default password for all seed accounts: "Password@123"
-- Format: <salt_hex>:<pbkdf2_sha256_hash_hex>
-- ============================================================================

-- 1. Sample Clusters
INSERT OR IGNORE INTO clusters (id, cluster_name) VALUES 
('cluster-01', 'North Cluster 01'),
('cluster-02', 'South Cluster 02');

-- 2. Sample Schools
INSERT OR IGNORE INTO schools (id, school_name, cluster_id, udise_code) VALUES 
('school-01', 'Government Model High School North', 'cluster-01', '27010100101'),
('school-02', 'Government Primary School South', 'cluster-02', '27010200202');

-- 3. Sample Users for Testing Roles (Password: Password@123)
-- PBKDF2 hash of "Password@123" with salt "0123456789abcdef0123456789abcdef" (100,000 iterations SHA-256):
INSERT OR IGNORE INTO users (id, name, email, password_hash, role, cluster_id, school_id, status) VALUES 
('user-admin-01', 'System App Admin', 'admin@kpdatasync.com', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'Admin', NULL, NULL, 'active'),
('user-cluster-01', 'Cluster Officer North', 'clusterhead@kpdatasync.com', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'Cluster_Head', 'cluster-01', NULL, 'active'),
('user-hm-01', 'Headmaster School 01', 'schoolhm@kpdatasync.com', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'School_HM', 'cluster-01', 'school-01', 'active'),
('user-teacher-01', 'Test Teacher 01', 'teacher@kpdatasync.com', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'Teacher', 'cluster-01', 'school-01', 'active');

-- 4. Sample Group
INSERT OR IGNORE INTO groups (id, group_name, created_by, scope) VALUES
('group-01', 'North Cluster Academic Coordination', 'user-admin-01', 'cluster');

-- 5. Sample Group Members
INSERT OR IGNORE INTO group_members (id, group_id, user_id) VALUES
('gm-01', 'group-01', 'user-admin-01'),
('gm-02', 'group-01', 'user-cluster-01'),
('gm-03', 'group-01', 'user-hm-01'),
('gm-04', 'group-01', 'user-teacher-01');

-- 6. Sample Messages
INSERT OR IGNORE INTO messages (id, group_id, sender_id, content, message_type) VALUES
('msg-01', 'group-01', 'user-admin-01', 'Welcome to North Cluster Academic Coordination.', 'text'),
('msg-02', 'group-01', 'user-cluster-01', 'Please submit monthly data reports by Friday.', 'text');

-- 7. Sample Task
INSERT OR IGNORE INTO tasks (id, title, description, assigned_to, school_id, created_by, status) VALUES
('task-01', 'Submit Term 1 Student Attendance', 'Upload updated Excel attendance spreadsheet to R2', 'user-teacher-01', 'school-01', 'user-admin-01', 'pending');
