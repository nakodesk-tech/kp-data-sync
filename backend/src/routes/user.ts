import { Hono } from 'hono';
import { Bindings, Variables } from '../types';
import { authMiddleware } from '../middleware/auth';

export const userRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

// GET /api/user/profile - Sample protected endpoint for all roles
userRouter.get('/profile', authMiddleware(), async (c) => {
  const currentUser = c.get('user');

  // Fetch fresh details along with school / cluster names
  const query = `
    SELECT 
      u.id, u.name, u.email, u.role, u.status, u.created_at,
      u.cluster_id, c.cluster_name,
      u.school_id, s.school_name, s.udise_code
    FROM users u
    LEFT JOIN clusters c ON u.cluster_id = c.id
    LEFT JOIN schools s ON u.school_id = s.id
    WHERE u.id = ?
  `;

  const profile = await c.env.DB.prepare(query).bind(currentUser.id).first();

  if (!profile) {
    return c.json({ success: false, error: 'User not found' }, 404);
  }

  return c.json({
    success: true,
    data: profile
  });
});

// GET /api/admin/overview - Protected endpoint for Admin and Cluster_Head only
userRouter.get(
  '/admin/overview',
  authMiddleware(['Admin', 'Cluster_Head']),
  async (c) => {
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
      // Cluster_Head: only their assigned cluster
      const schoolCount = await c.env.DB.prepare('SELECT COUNT(*) as count FROM schools WHERE cluster_id = ?')
        .bind(user.cluster_id)
        .first<{ count: number }>();
      const userCount = await c.env.DB.prepare('SELECT COUNT(*) as count FROM users WHERE cluster_id = ?')
        .bind(user.cluster_id)
        .first<{ count: number }>();

      totalSchools = schoolCount?.count || 0;
      totalUsers = userCount?.count || 0;
    }

    return c.json({
      success: true,
      data: {
        role: user.role,
        cluster_id: user.cluster_id,
        totalSchools,
        totalUsers,
        totalReports,
        r2_bucket_status: 'online'
      }
    });
  }
);
