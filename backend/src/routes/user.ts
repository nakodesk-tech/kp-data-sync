import { Hono } from 'hono';
import { Bindings, Variables, UserRole } from '../types';
import { authMiddleware } from '../middleware/auth';
import { hashPassword } from '../crypto';

export const userRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

// GET /api/user/profile - Protected endpoint for all roles.
userRouter.get('/profile', authMiddleware(), async (c) => {
  const currentUser = c.get('user');
  const profile = await c.env.DB
    .prepare(`
      SELECT id, name, email, mobile_number, role,
             cluster_name, cluster_code,
             school_name, school_code,
             address, status, created_at, updated_at
      FROM users
      WHERE id = ?
    `)
    .bind(currentUser.id)
    .first();

  if (!profile) return c.json({ success: false, error: 'User not found' }, 404);
  return c.json({ success: true, data: profile });
});

// POST /api/user/register - Create a non-Admin user inside the caller's RBAC scope.
// Admin: Cluster Head, School HM, Teacher
// Cluster Head: School HM, Teacher (same cluster)
// School HM: Teacher (same school)
userRouter.post('/register', authMiddleware(['Admin', 'Cluster_Head', 'School_HM']), async (c) => {
  const actor = c.get('user');

  let body: {
    name?: string;
    email?: string;
    mobile_number?: string;
    password?: string;
    role?: UserRole;
    cluster_name?: string;
    cluster_code?: string;
    school_name?: string;
    school_code?: string;
    address?: string;
  };

  try {
    body = await c.req.json();
  } catch {
    return c.json({ success: false, error: 'Invalid JSON request body' }, 400);
  }

  const name = body.name?.trim() || '';
  const email = body.email?.trim().toLowerCase() || '';
  const password = body.password || '';
  const requestedRole = body.role;
  const clusterCode = body.cluster_code?.trim() || null;
  const clusterName = body.cluster_name?.trim() || null;
  const schoolCode = body.school_code?.trim() || null;
  const schoolName = body.school_name?.trim() || null;
  const mobile = body.mobile_number?.trim() || null;
  const address = body.address?.trim() || null;

  if (name.length < 2) return c.json({ success: false, error: 'Name must contain at least 2 characters' }, 400);
  if (!/^\S+@\S+\.\S+$/.test(email)) return c.json({ success: false, error: 'A valid email address is required' }, 400);
  if (password.length < 8) return c.json({ success: false, error: 'Password must be at least 8 characters' }, 400);

  const allowedTargetRoles: UserRole[] = ['Cluster_Head', 'School_HM', 'Teacher'];
  if (!requestedRole || !allowedTargetRoles.includes(requestedRole)) {
    return c.json({ success: false, error: 'Admin accounts cannot be created through normal registration' }, 400);
  }

  // Enforce the same hierarchy on the server; the UI is never trusted for RBAC.
  if (actor.role === 'Cluster_Head' && !['School_HM', 'Teacher'].includes(requestedRole)) {
    return c.json({ success: false, error: 'Cluster Head can register only School HM or Teacher' }, 403);
  }
  if (actor.role === 'School_HM' && requestedRole !== 'Teacher') {
    return c.json({ success: false, error: 'School HM can register only Teacher accounts' }, 403);
  }

  if (requestedRole !== 'Cluster_Head' && !clusterCode) {
    return c.json({ success: false, error: 'Cluster Code is required for this role' }, 400);
  }
  if (requestedRole === 'Cluster_Head' && !clusterCode) {
    return c.json({ success: false, error: 'Cluster Code is required for Cluster Head' }, 400);
  }
  if ((requestedRole === 'School_HM' || requestedRole === 'Teacher') && !schoolCode) {
    return c.json({ success: false, error: 'School / UDISE Code is required for this role' }, 400);
  }

  if (actor.role === 'Cluster_Head' && clusterCode !== actor.cluster_code) {
    return c.json({ success: false, error: 'Registration is limited to your assigned cluster' }, 403);
  }
  if (actor.role === 'School_HM' && schoolCode !== actor.school_code) {
    return c.json({ success: false, error: 'Registration is limited to your assigned school' }, 403);
  }

  // For school-scoped users, validate the school and derive canonical names.
  let resolvedSchoolName = schoolName;
  let resolvedClusterName = clusterName;
  let resolvedClusterCode = clusterCode;

  if (schoolCode) {
    const school = await c.env.DB
      .prepare('SELECT school_name, cluster_name, cluster_code FROM schools WHERE udise_code = ? OR id = ? LIMIT 1')
      .bind(schoolCode, schoolCode)
      .first<{ school_name: string; cluster_name: string | null; cluster_code: string | null }>();

    if (!school) return c.json({ success: false, error: 'School / UDISE Code was not found' }, 400);
    resolvedSchoolName = school.school_name;
    if (!resolvedClusterName) resolvedClusterName = school.cluster_name;
    if (!resolvedClusterCode) resolvedClusterCode = school.cluster_code;

    if (actor.role === 'Cluster_Head' && school.cluster_code !== actor.cluster_code) {
      return c.json({ success: false, error: 'Selected school is outside your assigned cluster' }, 403);
    }
  }

  if (actor.role === 'Cluster_Head' && resolvedClusterCode !== actor.cluster_code) {
    return c.json({ success: false, error: 'Selected cluster is outside your assigned cluster' }, 403);
  }

  const existing = await c.env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(email).first();
  if (existing) return c.json({ success: false, error: 'An account with this email already exists' }, 409);

  const id = `user-${crypto.randomUUID()}`;
  const passwordHash = await hashPassword(password);

  try {
    await c.env.DB.prepare(`
      INSERT INTO users (
        id, name, email, mobile_number, password_hash, role,
        cluster_name, cluster_code, school_name, school_code,
        address, status
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active')
    `).bind(
      id, name, email, mobile, passwordHash, requestedRole,
      resolvedClusterName, resolvedClusterCode, resolvedSchoolName, schoolCode,
      address
    ).run();
  } catch (error) {
    console.error('User registration failed:', error);
    return c.json({ success: false, error: 'Unable to create user account' }, 500);
  }

  return c.json({
    success: true,
    data: {
      id,
      name,
      email,
      role: requestedRole,
      cluster_name: resolvedClusterName,
      cluster_code: resolvedClusterCode,
      school_name: resolvedSchoolName,
      school_code: schoolCode,
      status: 'active'
    }
  }, 201);
});

// GET /api/user/admin/overview - Admin and Cluster_Head only.
userRouter.get('/admin/overview', authMiddleware(['Admin', 'Cluster_Head']), async (c) => {
  const user = c.get('user');
  let totalSchools = 0;
  let totalUsers = 0;
  let totalReports = 0;

  if (user.role === 'Admin') {
    const schoolCount = await c.env.DB.prepare('SELECT COUNT(*) as count FROM schools').first<{ count: number }>();
    const userCount = await c.env.DB.prepare('SELECT COUNT(*) as count FROM users').first<{ count: number }>();
    const reportCount = await c.env.DB.prepare('SELECT COUNT(*) as count FROM reports').first<{ count: number }>();
    totalSchools = schoolCount?.count || 0;
    totalUsers = userCount?.count || 0;
    totalReports = reportCount?.count || 0;
  } else {
    const schoolCount = await c.env.DB
      .prepare('SELECT COUNT(*) as count FROM schools WHERE cluster_code = ?')
      .bind(user.cluster_code)
      .first<{ count: number }>();
    const userCount = await c.env.DB
      .prepare('SELECT COUNT(*) as count FROM users WHERE cluster_code = ?')
      .bind(user.cluster_code)
      .first<{ count: number }>();
    const reportCount = await c.env.DB
      .prepare(`
        SELECT COUNT(*) as count
        FROM reports r
        WHERE r.school_code IN (SELECT school_code FROM users WHERE cluster_code = ? AND school_code IS NOT NULL)
      `)
      .bind(user.cluster_code)
      .first<{ count: number }>();
    totalSchools = schoolCount?.count || 0;
    totalUsers = userCount?.count || 0;
    totalReports = reportCount?.count || 0;
  }

  return c.json({
    success: true,
    data: {
      role: user.role,
      cluster_name: user.cluster_name,
      cluster_code: user.cluster_code,
      totalSchools,
      totalUsers,
      totalReports,
      r2_bucket_status: 'online'
    }
  });
});
