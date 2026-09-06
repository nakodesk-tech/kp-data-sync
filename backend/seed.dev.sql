-- ============================================================================
-- KP Data Sync: Development & Testing Seed Data
-- ============================================================================
-- WARNING: DO NOT RUN THIS IN PRODUCTION!
-- Mock fixtures only. Cluster is represented by cluster_name/code fields.
-- Default password for all seed accounts: "Password@123"
-- ============================================================================

-- 1. Sample Schools
INSERT OR IGNORE INTO schools
(id, school_name, udise_code, cluster_name, cluster_code, taluka, district, hm_name, hm_mobile, school_type, is_active)
VALUES
('school-01', 'Government Model High School North', '27010100101', 'North Cluster 01', 'CL-01', 'Sample Taluka', 'Sample District', 'Headmaster School 01', '9000000001', 'Secondary', 1),
('school-02', 'Government Primary School South', '27010200202', 'South Cluster 02', 'CL-02', 'Sample Taluka', 'Sample District', 'Headmaster School 02', '9000000002', 'Primary', 1);

-- 2. Sample Users for Testing Roles (Password: Password@123)
-- PBKDF2 hash of "Password@123" with salt "0123456789abcdef0123456789abcdef" (100,000 iterations SHA-256).
INSERT OR IGNORE INTO users
(id, name, email, mobile_number, password_hash, role, cluster_name, cluster_code, school_name, school_code, address, status)
VALUES
('user-admin-01', 'System App Admin', 'admin@kpdatasync.com', '9000000010', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'Admin', NULL, NULL, NULL, NULL, NULL, 'active'),
('user-cluster-01', 'Cluster Officer North', 'clusterhead@kpdatasync.com', '9000000011', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'Cluster_Head', 'North Cluster 01', 'CL-01', NULL, NULL, NULL, 'active'),
('user-hm-01', 'Headmaster School 01', 'schoolhm@kpdatasync.com', '9000000012', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'School_HM', 'North Cluster 01', 'CL-01', 'Government Model High School North', '27010100101', NULL, 'active'),
('user-teacher-01', 'Test Teacher 01', 'teacher@kpdatasync.com', '9000000013', '0123456789abcdef0123456789abcdef:b99335ef00a6f8749a2a1dc1411516e885c393845b4c19ebba23c723f05c317f', 'Teacher', 'North Cluster 01', 'CL-01', 'Government Model High School North', '27010100101', NULL, 'active');

-- 3. Sample Group
INSERT OR IGNORE INTO groups
(id, group_name, group_type, created_by, cluster_code, description, is_active)
VALUES
('group-01', 'North Cluster Academic Coordination', 'cluster', 'user-admin-01', 'CL-01', 'Development sample group', 1);

-- 4. Sample Group Members
INSERT OR IGNORE INTO group_members
(id, group_id, group_name, user_id, user_name, role_in_group, is_active)
VALUES
('gm-01', 'group-01', 'North Cluster Academic Coordination', 'user-admin-01', 'System App Admin', 'admin', 1),
('gm-02', 'group-01', 'North Cluster Academic Coordination', 'user-cluster-01', 'Cluster Officer North', 'cluster_head', 1),
('gm-03', 'group-01', 'North Cluster Academic Coordination', 'user-hm-01', 'Headmaster School 01', 'school_hm', 1),
('gm-04', 'group-01', 'North Cluster Academic Coordination', 'user-teacher-01', 'Test Teacher 01', 'teacher', 1);

-- 5. Sample Messages
INSERT OR IGNORE INTO messages
(id, group_id, group_name, sender_id, sender_name, content, message_type)
VALUES
('msg-01', 'group-01', 'North Cluster Academic Coordination', 'user-admin-01', 'System App Admin', 'Welcome to North Cluster Academic Coordination.', 'text'),
('msg-02', 'group-01', 'North Cluster Academic Coordination', 'user-cluster-01', 'Cluster Officer North', 'Please submit monthly data reports by Friday.', 'text');

-- 6. Sample Task
INSERT OR IGNORE INTO tasks
(id, title, description, task_type, template_url, target_scope, target_code, created_by, status)
VALUES
('task-01', 'Submit Term 1 Student Attendance', 'Upload updated Excel attendance spreadsheet to R2', 'report', NULL, 'school', '27010100101', 'user-admin-01', 'pending');
