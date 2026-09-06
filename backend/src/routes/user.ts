import { Hono } from 'hono';
import { Bindings, Variables } from '../types';
import { authMiddleware } from '../middleware/auth';

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
