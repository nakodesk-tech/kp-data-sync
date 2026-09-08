import { Hono } from 'hono';
import { authMiddleware } from '../middleware/auth';
import { Bindings, Variables } from '../types';

export const groupManagementRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

function error(c: any, message: string, status = 400) {
  return c.json({ success: false, error: message }, status);
}

async function canManageGroup(db: D1Database, actor: any, groupId: string) {
  const group = await db.prepare(`
    SELECT id, group_name, scope_type, cluster_code, school_code, created_by, is_active
    FROM groups WHERE id = ? LIMIT 1
  `).bind(groupId).first<any>();
  if (!group || group.is_active !== 1) return { group: null, allowed: false };
  return { group, allowed: actor.role === 'Admin' || group.created_by === actor.id };
}

// Closed groups are visible only to App Admin / Cluster Head so they can be restored.
groupManagementRouter.get('/inactive', authMiddleware(['Admin', 'Cluster_Head']), async (c) => {
  const actor = c.get('user');
  try {
    const result = actor.role === 'Admin'
      ? await c.env.DB.prepare(`
          SELECT g.id, g.group_name, g.group_type, g.created_by, g.cluster_code, g.school_code,
                 g.scope_type, g.description, g.photo_key, g.is_active, g.created_at,
                 COALESCE((SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = g.id AND gm.is_active = 0), 0) AS member_count
          FROM groups g WHERE g.is_active = 0
          ORDER BY g.updated_at DESC, g.group_name COLLATE NOCASE ASC
        `).all()
      : await c.env.DB.prepare(`
          SELECT g.id, g.group_name, g.group_type, g.created_by, g.cluster_code, g.school_code,
                 g.scope_type, g.description, g.photo_key, g.is_active, g.created_at,
                 COALESCE((SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = g.id AND gm.is_active = 0), 0) AS member_count
          FROM groups g WHERE g.is_active = 0 AND g.scope_type = 'cluster' AND g.cluster_code = ?
          ORDER BY g.updated_at DESC, g.group_name COLLATE NOCASE ASC
        `).bind(actor.cluster_code).all();
    return c.json({ success: true, data: result.results || [] });
  } catch (e: any) {
    console.error('Inactive group directory failed:', e);
    return error(c, 'Unable to load inactive groups', 500);
  }
});

// Reactivation uses the same authority boundary as closing: App Admin or group owner.
groupManagementRouter.post('/:id/reactivate', authMiddleware(), async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('id');
  try {
    const group = await c.env.DB.prepare(`
      SELECT id, created_by, scope_type, cluster_code, is_active
      FROM groups WHERE id = ? LIMIT 1
    `).bind(groupId).first<any>();
    if (!group) return error(c, 'Group not found', 404);
    if (group.is_active === 1) return c.json({ success: true, is_active: true });
    if (actor.role !== 'Admin' && actor.id !== group.created_by) return error(c, 'Only the group owner or App Admin can reactivate this group', 403);
    if (actor.role === 'Cluster_Head' && (group.scope_type !== 'cluster' || group.cluster_code !== actor.cluster_code)) return error(c, 'This group is outside your assigned cluster', 403);

    await c.env.DB.batch([
      c.env.DB.prepare('UPDATE groups SET is_active = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?').bind(groupId),
      c.env.DB.prepare('UPDATE group_members SET is_active = 1 WHERE group_id = ?').bind(groupId)
    ]);
    return c.json({ success: true, is_active: true });
  } catch (e: any) {
    console.error('Group reactivation failed:', e);
    return error(c, 'Unable to reactivate group', 500);
  }
});

groupManagementRouter.get('/:id/management/members', authMiddleware(), async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('id');
  const access = await canManageGroup(c.env.DB, actor, groupId);
  if (!access.group) return error(c, 'Group not found', 404);
  if (!access.allowed) return error(c, 'Only the group owner or App Admin can manage members', 403);
  try {
    const result = await c.env.DB.prepare(`
      SELECT u.id, u.name, u.email, u.role, gm.role_in_group, gm.is_active
      FROM group_members gm
      INNER JOIN users u ON u.id = gm.user_id
      WHERE gm.group_id = ?
      ORDER BY CASE WHEN gm.role_in_group = 'owner' THEN 0 ELSE 1 END, u.name COLLATE NOCASE ASC
    `).bind(groupId).all();
    return c.json({ success: true, data: result.results || [] });
  } catch (e: any) {
    console.error('Managed group members failed:', e);
    return error(c, 'Unable to load managed group members', 500);
  }
});

groupManagementRouter.post('/:id/management/members', authMiddleware(), async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('id');
  const access = await canManageGroup(c.env.DB, actor, groupId);
  if (!access.group) return error(c, 'Group not found', 404);
  if (!access.allowed) return error(c, 'Only the group owner or App Admin can manage members', 403);

  const body = await c.req.json<any>().catch(() => ({}));
  const userId = typeof body.user_id === 'string' ? body.user_id.trim() : '';
  if (!userId) return error(c, 'User selection is required');

  try {
    const user = await c.env.DB.prepare(`SELECT id, name, role, status, cluster_code, school_code FROM users WHERE id = ? LIMIT 1`).bind(userId).first<any>();
    if (!user) return error(c, 'Selected registered user was not found', 404);
    if (user.status !== 'Active') return error(c, 'Inactive registered users cannot be added to a group', 400);

    if (access.group.scope_type === 'cluster' && user.cluster_code !== access.group.cluster_code) return error(c, 'Selected user is outside the group cluster scope', 403);
    if (access.group.scope_type === 'school' && user.school_code !== access.group.school_code) return error(c, 'Selected user is outside the group school scope', 403);

    const existing = await c.env.DB.prepare('SELECT id, is_active FROM group_members WHERE group_id = ? AND user_id = ? LIMIT 1').bind(groupId, userId).first<any>();
    if (existing) {
      if (existing.is_active === 1) return error(c, 'User is already an active group member', 409);
      await c.env.DB.prepare('UPDATE group_members SET is_active = 1 WHERE id = ?').bind(existing.id).run();
      return c.json({ success: true, reactivated: true });
    }

    const count = await c.env.DB.prepare('SELECT COUNT(*) AS count FROM group_members WHERE group_id = ? AND is_active = 1').bind(groupId).first<any>();
    if (Number(count?.count || 0) >= 200) return error(c, 'A group can contain at most 200 active members');

    await c.env.DB.prepare(`
      INSERT INTO group_members (id, group_id, group_name, user_id, user_name, role_in_group, is_active)
      VALUES (?, ?, ?, ?, ?, 'member', 1)
    `).bind(`gm-${crypto.randomUUID()}`, groupId, access.group.group_name, user.id, user.name).run();
    return c.json({ success: true });
  } catch (e: any) {
    console.error('Managed member add failed:', e);
    return error(c, 'Unable to add group member', 500);
  }
});

groupManagementRouter.patch('/:id/management/members/:userId', authMiddleware(), async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('id');
  const userId = c.req.param('userId');
  const access = await canManageGroup(c.env.DB, actor, groupId);
  if (!access.group) return error(c, 'Group not found', 404);
  if (!access.allowed) return error(c, 'Only the group owner or App Admin can manage members', 403);

  const body = await c.req.json<any>().catch(() => ({}));
  if (typeof body.is_active !== 'boolean') return error(c, 'Member active status is required');

  try {
    const member = await c.env.DB.prepare('SELECT id, role_in_group FROM group_members WHERE group_id = ? AND user_id = ? LIMIT 1').bind(groupId, userId).first<any>();
    if (!member) return error(c, 'Group member not found', 404);
    if (member.role_in_group === 'owner') return error(c, 'The group owner cannot be made inactive', 400);
    await c.env.DB.prepare('UPDATE group_members SET is_active = ? WHERE id = ?').bind(body.is_active ? 1 : 0, member.id).run();
    return c.json({ success: true, is_active: body.is_active });
  } catch (e: any) {
    console.error('Managed member status update failed:', e);
    return error(c, 'Unable to update member status', 500);
  }
});

groupManagementRouter.delete('/:id/management/members/:userId', authMiddleware(), async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('id');
  const userId = c.req.param('userId');
  const access = await canManageGroup(c.env.DB, actor, groupId);
  if (!access.group) return error(c, 'Group not found', 404);
  if (!access.allowed) return error(c, 'Only the group owner or App Admin can manage members', 403);

  try {
    const member = await c.env.DB.prepare('SELECT id, role_in_group FROM group_members WHERE group_id = ? AND user_id = ? LIMIT 1').bind(groupId, userId).first<any>();
    if (!member) return error(c, 'Group member not found', 404);
    if (member.role_in_group === 'owner') return error(c, 'The group owner cannot be removed', 400);
    await c.env.DB.prepare('DELETE FROM group_members WHERE id = ?').bind(member.id).run();
    return c.json({ success: true });
  } catch (e: any) {
    console.error('Managed member remove failed:', e);
    return error(c, 'Unable to remove group member', 500);
  }
});
