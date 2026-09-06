import { Hono } from 'hono';
import { Bindings, Variables, UserRole } from '../types';
import { authMiddleware } from '../middleware/auth';
import { hashPassword } from '../crypto';

export const userRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

userRouter.get('/profile', authMiddleware(), async (c) => {
  const currentUser = c.get('user');
  const profile = await c.env.DB.prepare(`
    SELECT id, name, email, mobile_number, role, cluster_name, cluster_code,
           school_name, school_code, address, status, created_at, updated_at
    FROM users WHERE id = ?
  `).bind(currentUser.id).first();
  if (!profile) return c.json({ success: false, error: 'User not found' }, 404);
  return c.json({ success: true, data: profile });
});

// Role-scoped user directory: Admin=all, Cluster Head=cluster, School HM/Teacher=school.
userRouter.get('/directory', authMiddleware(), async (c) => {
  const actor = c.get('user');
  try {
    let rows;
    if (actor.role === 'Admin') {
      rows = await c.env.DB.prepare(`SELECT id, name, email, mobile_number, role, cluster_name, cluster_code, school_name, school_code, address, status, created_at FROM users ORDER BY created_at DESC, name COLLATE NOCASE ASC`).all();
    } else if (actor.role === 'Cluster_Head') {
      rows = await c.env.DB.prepare(`SELECT id, name, email, mobile_number, role, cluster_name, cluster_code, school_name, school_code, address, status, created_at FROM users WHERE cluster_code = ? ORDER BY created_at DESC, name COLLATE NOCASE ASC`).bind(actor.cluster_code).all();
    } else {
      rows = await c.env.DB.prepare(`SELECT id, name, email, mobile_number, role, cluster_name, cluster_code, school_name, school_code, address, status, created_at FROM users WHERE school_code = ? ORDER BY created_at DESC, name COLLATE NOCASE ASC`).bind(actor.school_code).all();
    }
    return c.json({ success: true, data: rows.results || [] });
  } catch (error: any) {
    console.error('User directory failed:', error);
    return c.json({ success: false, error: `Database error while loading users: ${error?.message || 'unknown database error'}` }, 500);
  }
});

// POST /api/user/register - Admin -> Cluster Head/HM/Teacher; Cluster Head -> HM/Teacher; HM -> Teacher.
userRouter.post('/register', authMiddleware(['Admin', 'Cluster_Head', 'School_HM']), async (c) => {
  const actor = c.get('user');
  let body: { name?: string; email?: string; mobile_number?: string; password?: string; role?: UserRole; cluster_name?: string; cluster_code?: string; school_name?: string; school_code?: string; address?: string; };
  try { body = await c.req.json(); } catch { return c.json({ success: false, error: 'Invalid JSON request body' }, 400); }
  const name = body.name?.trim() || '';
  const email = body.email?.trim().toLowerCase() || '';
  const password = body.password || '';
  const requestedRole = body.role;
  const mobile = body.mobile_number?.trim() || null;
  const clusterName = body.cluster_name?.trim() || null;
  const clusterCode = body.cluster_code?.trim() || null;
  const schoolName = body.school_name?.trim() || null;
  const schoolCode = body.school_code?.trim() || null;
  const address = body.address?.trim() || null;
  if (name.length < 2) return c.json({ success: false, error: 'Name must contain at least 2 characters' }, 400);
  if (!/^\S+@\S+\.\S+$/.test(email)) return c.json({ success: false, error: 'A valid email address is required' }, 400);
  if (password.length < 8) return c.json({ success: false, error: 'Password must be at least 8 characters' }, 400);
  const allowedTargetRoles: UserRole[] = ['Cluster_Head', 'School_HM', 'Teacher'];
  if (!requestedRole || !allowedTargetRoles.includes(requestedRole)) return c.json({ success: false, error: 'Admin accounts cannot be created through normal registration' }, 400);
  if (actor.role === 'Cluster_Head' && !['School_HM', 'Teacher'].includes(requestedRole)) return c.json({ success: false, error: 'Cluster Head can register only School HM or Teacher' }, 403);
  if (actor.role === 'School_HM' && requestedRole !== 'Teacher') return c.json({ success: false, error: 'School HM can register only Teacher accounts' }, 403);
  if (!clusterCode) return c.json({ success: false, error: 'Cluster Code is required for user registration' }, 400);
  if ((requestedRole === 'School_HM' || requestedRole === 'Teacher') && !schoolCode) return c.json({ success: false, error: 'School / UDISE Code is required for this role' }, 400);
  if (actor.role === 'Cluster_Head' && clusterCode !== actor.cluster_code) return c.json({ success: false, error: 'Registration is limited to your assigned cluster' }, 403);
  if (actor.role === 'School_HM' && schoolCode !== actor.school_code) return c.json({ success: false, error: 'Registration is limited to your assigned school' }, 403);
  let resolvedSchoolName = schoolName; let resolvedClusterName = clusterName; let resolvedClusterCode = clusterCode; let resolvedSchoolCode = schoolCode;
  try {
    if (schoolCode) {
      const school = await c.env.DB.prepare(`SELECT school_name, udise_code, cluster_name, cluster_code, is_active FROM schools WHERE udise_code = ? OR id = ? LIMIT 1`).bind(schoolCode, schoolCode).first<{ school_name: string; udise_code: string | null; cluster_name: string | null; cluster_code: string | null; is_active: number | null }>();
      if (!school) return c.json({ success: false, error: 'School / UDISE Code was not found in the registered schools directory' }, 400);
      if (school.is_active !== 1) return c.json({ success: false, error: 'Selected school is inactive. Activate the school before registering users.' }, 400);
      resolvedSchoolName = school.school_name; resolvedSchoolCode = school.udise_code || schoolCode; resolvedClusterName = school.cluster_name || resolvedClusterName; resolvedClusterCode = school.cluster_code || resolvedClusterCode;
      if (!resolvedClusterCode) return c.json({ success: false, error: 'Selected school has no Cluster Code. Update the school master first.' }, 400);
      if (actor.role === 'Cluster_Head' && resolvedClusterCode !== actor.cluster_code) return c.json({ success: false, error: 'Selected school is outside your assigned cluster' }, 403);
    }
    if (actor.role === 'Cluster_Head' && resolvedClusterCode !== actor.cluster_code) return c.json({ success: false, error: 'Selected cluster is outside your assigned cluster' }, 403);
    const existing = await c.env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(email).first();
    if (existing) return c.json({ success: false, error: 'An account with this email already exists' }, 409);
    const id = `user-${crypto.randomUUID()}`; const passwordHash = await hashPassword(password);
    try {
      await c.env.DB.prepare(`INSERT INTO users (id, name, email, mobile_number, password_hash, role, cluster_name, cluster_code, school_name, school_code, address, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Active')`).bind(id, name, email, mobile, passwordHash, requestedRole, resolvedClusterName, resolvedClusterCode, resolvedSchoolName, resolvedSchoolCode, address).run();
    } catch (error: any) { console.error('User registration INSERT failed:', error); return c.json({ success: false, error: `Database error while creating user: ${error?.message || 'unknown database error'}` }, 500); }
    return c.json({ success: true, data: { id, name, email, role: requestedRole, mobile_number: mobile, cluster_name: resolvedClusterName, cluster_code: resolvedClusterCode, school_name: resolvedSchoolName, school_code: resolvedSchoolCode, address, status: 'Active' } }, 201);
  } catch (error: any) { console.error('User registration failed:', error); return c.json({ success: false, error: `Database error during user registration: ${error?.message || 'unknown database error'}` }, 500); }
});

userRouter.patch('/:id', authMiddleware(['Admin', 'Cluster_Head', 'School_HM']), async (c) => {
  const actor = c.get('user'); const id = c.req.param('id');
  try {
    const target = await c.env.DB.prepare(`SELECT id, name, email, mobile_number, role, cluster_code, school_code, address, status FROM users WHERE id = ?`).bind(id).first<any>();
    if (!target) return c.json({ success: false, error: 'User not found' }, 404);
    if (target.id === actor.id) return c.json({ success: false, error: 'Your own account cannot be changed from user management' }, 403);
    if (actor.role === 'Cluster_Head' && target.cluster_code !== actor.cluster_code) return c.json({ success: false, error: 'User is outside your assigned cluster' }, 403);
    if (actor.role === 'School_HM' && target.school_code !== actor.school_code) return c.json({ success: false, error: 'User is outside your assigned school' }, 403);
    if (actor.role === 'Cluster_Head' && !['School_HM', 'Teacher'].includes(target.role)) return c.json({ success: false, error: 'Cluster Head cannot manage this role' }, 403);
    if (actor.role === 'School_HM' && target.role !== 'Teacher') return c.json({ success: false, error: 'School HM can manage only Teacher accounts' }, 403);
    let body: Record<string, unknown> = {}; try { body = await c.req.json(); } catch { return c.json({ success: false, error: 'Invalid JSON request body' }, 400); }
    const name = typeof body.name === 'string' ? body.name.trim() : target.name;
    const mobile = typeof body.mobile_number === 'string' ? body.mobile_number.trim() : (target.mobile_number || null);
    const address = typeof body.address === 'string' ? body.address.trim() : (target.address || null);
    const status = typeof body.status === 'string' ? body.status : target.status;
    if (name.length < 2) return c.json({ success: false, error: 'Name must contain at least 2 characters' }, 400);
    if (!['Active', 'Inactive'].includes(status)) return c.json({ success: false, error: 'Status must be Active or Inactive' }, 400);
    await c.env.DB.prepare(`UPDATE users SET name = ?, mobile_number = ?, address = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?`).bind(name, mobile, address, status, id).run();
    return c.json({ success: true, message: 'User updated successfully' });
  } catch (error: any) { console.error('User update failed:', error); return c.json({ success: false, error: `Database error during user update: ${error?.message || 'unknown database error'}` }, 500); }
});

userRouter.delete('/:id', authMiddleware(['Admin', 'Cluster_Head', 'School_HM']), async (c) => {
  const actor = c.get('user'); const id = c.req.param('id');
  try {
    const target = await c.env.DB.prepare(`SELECT id, name, role, cluster_code, school_code FROM users WHERE id = ?`).bind(id).first<any>();
    if (!target) return c.json({ success: false, error: 'User not found' }, 404);
    if (target.id === actor.id) return c.json({ success: false, error: 'Your own account cannot be deleted' }, 403);
    if (actor.role === 'Cluster_Head' && (target.cluster_code !== actor.cluster_code || !['School_HM', 'Teacher'].includes(target.role))) return c.json({ success: false, error: 'User is outside your management scope' }, 403);
    if (actor.role === 'School_HM' && (target.school_code !== actor.school_code || target.role !== 'Teacher')) return c.json({ success: false, error: 'Only teachers in your school can be deleted' }, 403);
    const refs = await Promise.all([
      c.env.DB.prepare('SELECT COUNT(*) as count FROM group_members WHERE user_id = ?').bind(id).first<{ count: number }>(),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM messages WHERE sender_id = ?').bind(id).first<{ count: number }>(),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM tasks WHERE created_by = ?').bind(id).first<{ count: number }>(),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM reports WHERE submitted_by = ?').bind(id).first<{ count: number }>()
    ]);
    if (refs.some(r => (r?.count || 0) > 0)) return c.json({ success: false, error: 'This user has linked records. Deactivate the account instead of deleting it.' }, 409);
    await c.env.DB.prepare('DELETE FROM users WHERE id = ?').bind(id).run();
    return c.json({ success: true, message: 'User deleted successfully' });
  } catch (error: any) { console.error('User deletion failed:', error); return c.json({ success: false, error: `Database error during user deletion: ${error?.message || 'unknown database error'}` }, 500); }
});

userRouter.get('/admin/overview', authMiddleware(['Admin', 'Cluster_Head']), async (c) => {
  const user = c.get('user');
  let totalSchools = 0; let totalUsers = 0; let totalReports = 0;
  if (user.role === 'Admin') {
    const schoolCount = await c.env.DB.prepare('SELECT COUNT(*) as count FROM schools').first<{ count: number }>();
    const userCount = await c.env.DB.prepare('SELECT COUNT(*) as count FROM users').first<{ count: number }>();
    const reportCount = await c.env.DB.prepare('SELECT COUNT(*) as count FROM reports').first<{ count: number }>();
    totalSchools = schoolCount?.count || 0; totalUsers = userCount?.count || 0; totalReports = reportCount?.count || 0;
  } else {
    const schoolCount = await c.env.DB.prepare('SELECT COUNT(*) as count FROM schools WHERE cluster_code = ?').bind(user.cluster_code).first<{ count: number }>();
    const userCount = await c.env.DB.prepare('SELECT COUNT(*) as count FROM users WHERE cluster_code = ?').bind(user.cluster_code).first<{ count: number }>();
    const reportCount = await c.env.DB.prepare(`SELECT COUNT(*) as count FROM reports r WHERE r.school_code IN (SELECT school_code FROM users WHERE cluster_code = ? AND school_code IS NOT NULL)`).bind(user.cluster_code).first<{ count: number }>();
    totalSchools = schoolCount?.count || 0; totalUsers = userCount?.count || 0; totalReports = reportCount?.count || 0;
  }
  return c.json({ success: true, data: { role: user.role, cluster_name: user.cluster_name, cluster_code: user.cluster_code, totalSchools, totalUsers, totalReports, r2_bucket_status: 'online' } });
});
