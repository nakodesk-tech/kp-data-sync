import { Hono } from 'hono';
import { Bindings, Variables } from '../types';
import { authMiddleware } from '../middleware/auth';

export const schoolRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

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

  try {
    body = await c.req.json();
  } catch {
    return c.json({ success: false, error: 'Invalid JSON request body' }, 400);
  }

  const schoolName = body.school_name?.trim() || '';
  const udiseCode = body.udise_code?.trim() || null;
  const clusterName = body.cluster_name?.trim() || null;
  const clusterCode = body.cluster_code?.trim() || null;
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
      INSERT INTO schools (
        id, school_name, udise_code, cluster_name, cluster_code,
        taluka, district, hm_name, hm_mobile, school_type, is_active
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      id, schoolName, udiseCode, clusterName, clusterCode,
      taluka, district, hmName, hmMobile, schoolType, isActive
    ).run();

    return c.json({
      success: true,
      message: 'School registered successfully',
      data: {
        id,
        school_name: schoolName,
        udise_code: udiseCode,
        cluster_name: clusterName,
        cluster_code: clusterCode,
        taluka,
        district,
        hm_name: hmName,
        hm_mobile: hmMobile,
        school_type: schoolType,
        is_active: isActive
      }
    }, 201);
  } catch (error: any) {
    console.error('School registration failed:', error);
    return c.json({ success: false, error: `Database error during school registration: ${error?.message || 'unknown database error'}` }, 500);
  }
});
