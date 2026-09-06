import { Hono } from 'hono';
import { Bindings, Variables } from '../types';
import { authMiddleware } from '../middleware/auth';

export const schoolRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

// GET /api/schools - Live school directory for authenticated users.
schoolRouter.get('/', authMiddleware(), async (c) => {
  try {
    const rows = await c.env.DB.prepare(`
      SELECT id, school_name, udise_code, cluster_name, cluster_code,
             taluka, district, hm_name, hm_mobile, school_type, is_active,
             created_at, updated_at
      FROM schools
      ORDER BY is_active DESC, school_name COLLATE NOCASE ASC
    `).all();
    return c.json({ success: true, data: rows.results || [] });
  } catch (error: any) {
    console.error('School list failed:', error);
    return c.json({ success: false, error: `Database error while loading schools: ${error?.message || 'unknown database error'}` }, 500);
  }
});

// POST /api/schools/register - Admin-only school registration.
schoolRouter.post('/register', authMiddleware(['Admin']), async (c) => {
  let body: {
    school_name?: string;
    udise_code?: string;
    cluster_name?: string;
    cluster_code?: string;
    taluka?: string;
    district?: string;
    hm_name?: string;
    hm_mobile?: string;
    school_type?: string;
    is_active?: boolean;
  };

  try { body = await c.req.json(); }
  catch { return c.json({ success: false, error: 'Invalid JSON request body' }, 400); }

  const schoolName = body.school_name?.trim() || '';
  const udiseCode = body.udise_code?.trim() || '';
  const clusterName = body.cluster_name?.trim() || '';
  const clusterCode = body.cluster_code?.trim() || '';
  const taluka = body.taluka?.trim() || null;
  const district = body.district?.trim() || null;
  const hmName = body.hm_name?.trim() || null;
  const hmMobile = body.hm_mobile?.trim() || null;
  const schoolType = body.school_type?.trim() || null;
  const isActive = typeof body.is_active === 'boolean' ? (body.is_active ? 1 : 0) : 1;

  if (!schoolName) return c.json({ success: false, error: 'School Name is required' }, 400);
  if (!udiseCode) return c.json({ success: false, error: 'UDISE / School Code is required for user registration mapping' }, 400);
  if (!clusterName) return c.json({ success: false, error: 'Cluster Name is required' }, 400);
  if (!clusterCode) return c.json({ success: false, error: 'Cluster Code is required' }, 400);

  try {
    const existing = await c.env.DB.prepare('SELECT id FROM schools WHERE udise_code = ?').bind(udiseCode).first<{ id: string }>();
    if (existing) return c.json({ success: false, error: 'A school with this UDISE / School Code already exists' }, 409);

    const id = `school-${crypto.randomUUID()}`;
    await c.env.DB.prepare(`
      INSERT INTO schools (id, school_name, udise_code, cluster_name, cluster_code, taluka, district, hm_name, hm_mobile, school_type, is_active)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(id, schoolName, udiseCode, clusterName, clusterCode, taluka, district, hmName, hmMobile, schoolType, isActive).run();

    return c.json({ success: true, message: 'School registered successfully', data: { id, school_name: schoolName, udise_code: udiseCode, cluster_name: clusterName, cluster_code: clusterCode, taluka, district, hm_name: hmName, hm_mobile: hmMobile, school_type: schoolType, is_active: isActive } }, 201);
  } catch (error: any) {
    console.error('School registration failed:', error);
    return c.json({ success: false, error: `Database error during school registration: ${error?.message || 'unknown database error'}` }, 500);
  }
});

// PATCH /api/schools/:id - Admin school master update/status change.
schoolRouter.patch('/:id', authMiddleware(['Admin']), async (c) => {
  const id = c.req.param('id');
  let body: Record<string, unknown>;
  try { body = await c.req.json(); }
  catch { return c.json({ success: false, error: 'Invalid JSON request body' }, 400); }

  try {
    const existing = await c.env.DB.prepare('SELECT * FROM schools WHERE id = ?').bind(id).first<any>();
    if (!existing) return c.json({ success: false, error: 'School not found' }, 404);

    const schoolName = typeof body.school_name === 'string' ? body.school_name.trim() : existing.school_name;
    const clusterName = typeof body.cluster_name === 'string' ? body.cluster_name.trim() : existing.cluster_name;
    const clusterCode = typeof body.cluster_code === 'string' ? body.cluster_code.trim() : existing.cluster_code;
    const taluka = typeof body.taluka === 'string' ? body.taluka.trim() : existing.taluka;
    const district = typeof body.district === 'string' ? body.district.trim() : existing.district;
    const hmName = typeof body.hm_name === 'string' ? body.hm_name.trim() : existing.hm_name;
    const hmMobile = typeof body.hm_mobile === 'string' ? body.hm_mobile.trim() : existing.hm_mobile;
    const schoolType = typeof body.school_type === 'string' ? body.school_type.trim() : existing.school_type;
    const isActive = typeof body.is_active === 'boolean' ? (body.is_active ? 1 : 0) : existing.is_active;

    if (!schoolName || !clusterName || !clusterCode) return c.json({ success: false, error: 'School Name, Cluster Name and Cluster Code are required' }, 400);

    await c.env.DB.prepare(`
      UPDATE schools
      SET school_name = ?, cluster_name = ?, cluster_code = ?, taluka = ?, district = ?, hm_name = ?, hm_mobile = ?, school_type = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP
      WHERE id = ?
    `).bind(schoolName, clusterName, clusterCode, taluka || null, district || null, hmName || null, hmMobile || null, schoolType || null, isActive, id).run();

    return c.json({ success: true, message: 'School updated successfully' });
  } catch (error: any) {
    console.error('School update failed:', error);
    return c.json({ success: false, error: `Database error during school update: ${error?.message || 'unknown database error'}` }, 500);
  }
});

// DELETE /api/schools/:id - Admin-only hard delete, guarded when users are linked.
schoolRouter.delete('/:id', authMiddleware(['Admin']), async (c) => {
  const id = c.req.param('id');
  try {
    const existing = await c.env.DB.prepare('SELECT id, school_name, udise_code FROM schools WHERE id = ?').bind(id).first<{ id: string; school_name: string; udise_code: string | null }>();
    if (!existing) return c.json({ success: false, error: 'School not found' }, 404);

    const linkedUsers = await c.env.DB.prepare('SELECT COUNT(*) as count FROM users WHERE school_code = ?').bind(existing.udise_code).first<{ count: number }>();
    if ((linkedUsers?.count || 0) > 0) {
      return c.json({ success: false, error: 'This school has registered users. Deactivate the school instead of deleting it.' }, 409);
    }

    await c.env.DB.prepare('DELETE FROM schools WHERE id = ?').bind(id).run();
    return c.json({ success: true, message: 'School deleted successfully' });
  } catch (error: any) {
    console.error('School deletion failed:', error);
    return c.json({ success: false, error: `Database error during school deletion: ${error?.message || 'unknown database error'}` }, 500);
  }
});
