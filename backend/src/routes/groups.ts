import { Hono } from 'hono';
import { authMiddleware } from '../middleware/auth';
import { Bindings, Variables } from '../types';

export const groupRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

const ALLOWED_GROUP_TYPES = new Set(['administrative', 'cluster', 'school', 'general']);
const ALLOWED_SCOPE_TYPES = new Set(['system', 'cluster', 'school']);
const ALLOWED_PHOTO_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const MAX_MEMBERS = 200;

type GroupPhotoFile = {
  size: number;
  type: string;
  stream: () => ReadableStream<Uint8Array>;
};

function error(c: any, message: string, status = 400) {
  return c.json({ success: false, error: message }, status);
}

async function canAccessGroup(db: D1Database, actor: any, groupId: string) {
  const group = await db.prepare(`
    SELECT id, group_name, group_type, created_by, scope_type, cluster_code, school_code, description,
           photo_key, is_active, created_at, updated_at
    FROM groups WHERE id = ?
  `).bind(groupId).first<any>();
  if (!group) return { group: null, allowed: false };
  if (actor.role === 'Admin') return { group, allowed: true };
  if (actor.role === 'Cluster_Head') return { group, allowed: group.scope_type === 'cluster' && group.cluster_code === actor.cluster_code };
  if (actor.role === 'School_HM' || actor.role === 'Teacher') {
    const member = await db.prepare('SELECT id FROM group_members WHERE group_id = ? AND user_id = ? AND is_active = 1 LIMIT 1').bind(groupId, actor.id).first();
    return { group, allowed: !!member };
  }
  return { group, allowed: false };
}

// Role-scoped group directory. Members can see groups they belong to; Admin sees all;
// Cluster Head sees groups in the assigned cluster.
groupRouter.get('/', authMiddleware(), async (c) => {
  const actor = c.get('user');
  try {
    let result;
    if (actor.role === 'Admin') {
      result = await c.env.DB.prepare(`
        SELECT g.id, g.group_name, g.group_type, g.created_by, g.cluster_code, g.school_code,
               g.scope_type, g.description, g.photo_key, g.is_active, g.created_at,
               COALESCE((SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = g.id AND gm.is_active = 1), 0) AS member_count
        FROM groups g WHERE g.is_active = 1 ORDER BY g.created_at DESC, g.group_name COLLATE NOCASE ASC
      `).all();
    } else if (actor.role === 'Cluster_Head') {
      result = await c.env.DB.prepare(`
        SELECT g.id, g.group_name, g.group_type, g.created_by, g.cluster_code, g.school_code,
               g.scope_type, g.description, g.photo_key, g.is_active, g.created_at,
               COALESCE((SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = g.id AND gm.is_active = 1), 0) AS member_count
        FROM groups g WHERE g.is_active = 1 AND g.scope_type = 'cluster' AND g.cluster_code = ?
        ORDER BY g.created_at DESC, g.group_name COLLATE NOCASE ASC
      `).bind(actor.cluster_code).all();
    } else {
      result = await c.env.DB.prepare(`
        SELECT g.id, g.group_name, g.group_type, g.created_by, g.cluster_code, g.school_code,
               g.scope_type, g.description, g.photo_key, g.is_active, g.created_at,
               COALESCE((SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = g.id AND gm.is_active = 1), 0) AS member_count
        FROM groups g INNER JOIN group_members gm ON gm.group_id = g.id
        WHERE g.is_active = 1 AND gm.user_id = ? AND gm.is_active = 1
        ORDER BY g.created_at DESC, g.group_name COLLATE NOCASE ASC
      `).bind(actor.id).all();
    }
    return c.json({ success: true, data: result.results || [] });
  } catch (e: any) {
    console.error('Group directory failed:', e);
    return error(c, `Database error while loading groups: ${e?.message || 'unknown database error'}`, 500);
  }
});

// Full group creation endpoint. The optional avatar is uploaded to R2 in the same request.
groupRouter.post('/', authMiddleware(['Admin', 'Cluster_Head']), async (c) => {
  const actor = c.get('user');
  let form: FormData;
  try {
    form = await c.req.formData();
  } catch {
    return error(c, 'Invalid multipart form data');
  }

  const groupName = String(form.get('group_name') || '').trim();
  const description = String(form.get('description') || '').trim();
  const groupType = String(form.get('group_type') || '').trim();
  const requestedScope = String(form.get('scope_type') || '').trim();
  const clusterCode = String(form.get('cluster_code') || '').trim() || null;
  const schoolCode = String(form.get('school_code') || '').trim() || null;
  const rawMembers = String(form.get('member_ids') || '[]');
  const photoValue: unknown = form.get('photo');
  let photoFile: GroupPhotoFile | null = null;

  if (photoValue !== null && typeof photoValue === 'object') {
    const candidate = photoValue as Record<string, unknown>;
    if (
      typeof candidate.size === 'number' &&
      typeof candidate.type === 'string' &&
      typeof candidate.stream === 'function'
    ) {
      photoFile = photoValue as GroupPhotoFile;
    }
  }

  if (groupName.length < 2 || groupName.length > 80) return error(c, 'Group name must contain 2 to 80 characters');
  if (description.length > 500) return error(c, 'Group description cannot exceed 500 characters');
  if (!ALLOWED_GROUP_TYPES.has(groupType)) return error(c, 'Invalid group type');
  if (!ALLOWED_SCOPE_TYPES.has(requestedScope)) return error(c, 'Invalid group scope');

  let memberIds: string[];
  try {
    const parsed = JSON.parse(rawMembers);
    if (!Array.isArray(parsed)) return error(c, 'Member selection is invalid');
    memberIds = [...new Set(parsed.map((id) => String(id).trim()).filter(Boolean))];
  } catch {
    return error(c, 'Member selection is invalid');
  }
  if (memberIds.length > MAX_MEMBERS) return error(c, `A group can contain at most ${MAX_MEMBERS} selected members`);

  // Cluster Head is always restricted to exactly their assigned cluster.
  let effectiveScope = requestedScope;
  let effectiveClusterCode = clusterCode;
  let effectiveSchoolCode = schoolCode;
  if (actor.role === 'Cluster_Head') {
    effectiveScope = 'cluster';
    effectiveClusterCode = actor.cluster_code || null;
    effectiveSchoolCode = null;
    if (!effectiveClusterCode) return error(c, 'Your account has no assigned cluster. Group creation is unavailable until the cluster mapping is fixed.', 403);
  }

  if (effectiveScope === 'system') {
    if (actor.role !== 'Admin') return error(c, 'Only App Admin can create a system-wide group', 403);
    effectiveClusterCode = null;
    effectiveSchoolCode = null;
  }
  if (effectiveScope === 'cluster' && !effectiveClusterCode) return error(c, 'Cluster Code is required for a cluster group');
  if (effectiveScope === 'school' && !effectiveSchoolCode) return error(c, 'School / UDISE Code is required for a school group');

  try {
    // Validate the selected scope against the live school master where applicable.
    if (effectiveScope === 'cluster') {
      const cluster = await c.env.DB.prepare('SELECT 1 FROM schools WHERE cluster_code = ? AND is_active = 1 LIMIT 1').bind(effectiveClusterCode).first();
      if (!cluster) return error(c, 'The selected cluster has no active registered schools', 400);
    }
    if (effectiveScope === 'school') {
      const school = await c.env.DB.prepare('SELECT udise_code, cluster_code, is_active FROM schools WHERE udise_code = ? OR id = ? LIMIT 1').bind(effectiveSchoolCode, effectiveSchoolCode).first<any>();
      if (!school || school.is_active !== 1) return error(c, 'Selected school is not an active registered school', 400);
      effectiveSchoolCode = school.udise_code || effectiveSchoolCode;
      effectiveClusterCode = school.cluster_code || null;
      if (!effectiveClusterCode) return error(c, 'Selected school has no Cluster Code', 400);
      if (actor.role === 'Cluster_Head' && effectiveClusterCode !== actor.cluster_code) return error(c, 'Selected school is outside your assigned cluster', 403);
    }

    // The creator is always an owner/member. Additional members must be active users
    // and must fall inside the requested scope.
    const allMemberIds = [...new Set([actor.id, ...memberIds])];
    if (allMemberIds.length > MAX_MEMBERS + 1) return error(c, `A group can contain at most ${MAX_MEMBERS} selected members`);

    const placeholders = allMemberIds.map(() => '?').join(',');
    const users = await c.env.DB.prepare(`SELECT id, name, role, cluster_code, school_code, status FROM users WHERE id IN (${placeholders})`).bind(...allMemberIds).all<any>();
    const userRows = users.results || [];
    const byId = new Map(userRows.map((u: any) => [u.id, u]));
    for (const id of allMemberIds) {
      const user = byId.get(id);
      if (!user) return error(c, 'One or more selected users no longer exist', 400);
      if (user.status !== 'Active') return error(c, `User '${user.name}' is inactive and cannot be added to a new group`, 400);
      if (actor.role === 'Cluster_Head' && user.cluster_code !== actor.cluster_code) return error(c, `User '${user.name}' is outside your assigned cluster`, 403);
      if (effectiveScope === 'cluster' && user.cluster_code !== effectiveClusterCode) return error(c, `User '${user.name}' is outside the selected cluster`, 400);
      if (effectiveScope === 'school' && user.school_code !== effectiveSchoolCode) return error(c, `User '${user.name}' is outside the selected school`, 400);
    }

    if (photoFile && photoFile.size > 0) {
      if (!ALLOWED_PHOTO_TYPES.has(photoFile.type)) return error(c, 'Group photo must be JPG, PNG or WebP');
      if (photoFile.size > MAX_PHOTO_BYTES) return error(c, 'Group photo must be 5 MB or smaller');
    }

    const groupId = `group-${crypto.randomUUID()}`;
    let photoKey: string | null = null;
    if (photoFile && photoFile.size > 0) {
      const extension = photoFile.type === 'image/png' ? 'png' : photoFile.type === 'image/webp' ? 'webp' : 'jpg';
      photoKey = `groups/${groupId}/avatar.${extension}`;
      await c.env.R2_BUCKET.put(photoKey, photoFile.stream(), {
        httpMetadata: { contentType: photoFile.type, cacheControl: 'public, max-age=31536000, immutable' },
        customMetadata: { groupId, uploadedBy: actor.id }
      });
    }

    try {
      // D1 batch makes group + membership creation atomic: if any membership insert fails,
      // the group insert is rolled back as well.
      const statements: D1PreparedStatement[] = [
        c.env.DB.prepare(`
          INSERT INTO groups (id, group_name, group_type, created_by, cluster_code, school_code, scope_type, description, photo_key, is_active)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
        `).bind(groupId, groupName, groupType, actor.id, effectiveClusterCode, effectiveSchoolCode, effectiveScope, description || null, photoKey)
      ];

      for (const user of userRows) {
        statements.push(c.env.DB.prepare(`
          INSERT INTO group_members (id, group_id, group_name, user_id, user_name, role_in_group, is_active)
          VALUES (?, ?, ?, ?, ?, ?, 1)
        `).bind(`gm-${crypto.randomUUID()}`, groupId, groupName, user.id, user.name, user.id === actor.id ? 'owner' : 'member'));
      }

      await c.env.DB.batch(statements);
    } catch (dbError: any) {
      if (photoKey) await c.env.R2_BUCKET.delete(photoKey).catch(() => undefined);
      throw dbError;
    }

    return c.json({
      success: true,
      data: {
        id: groupId,
        group_name: groupName,
        group_type: groupType,
        created_by: actor.id,
        cluster_code: effectiveClusterCode,
        school_code: effectiveSchoolCode,
        scope_type: effectiveScope,
        description: description || null,
        photo_key: photoKey,
        member_count: allMemberIds.length,
        is_active: 1,
        created_at: new Date().toISOString()
      }
    }, 201);
  } catch (e: any) {
    console.error('Group creation failed:', e);
    return error(c, `Database error while creating group: ${e?.message || 'unknown database error'}`, 500);
  }
});

// Authenticated photo streaming endpoint. The app can later use this URL with its Bearer token.
groupRouter.get('/:id/photo', authMiddleware(), async (c) => {
  const actor = c.get('user');
  const id = c.req.param('id');
  try {
    const access = await canAccessGroup(c.env.DB, actor, id);
    if (!access.group) return error(c, 'Group not found', 404);
    if (!access.allowed) return error(c, 'You do not have access to this group', 403);
    if (!access.group.photo_key) return error(c, 'This group has no photo', 404);
    const object = await c.env.R2_BUCKET.get(access.group.photo_key);
    if (!object) return error(c, 'Group photo not found', 404);
    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set('Cache-Control', 'private, max-age=3600');
    return new Response(object.body, { status: 200, headers });
  } catch (e: any) {
    console.error('Group photo read failed:', e);
    return error(c, 'Unable to load group photo', 500);
  }
});
