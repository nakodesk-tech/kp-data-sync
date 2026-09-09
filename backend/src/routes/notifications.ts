import { Hono } from 'hono';
import { authMiddleware } from '../middleware/auth';
import { Bindings, Variables } from '../types';

export const notificationRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

function error(c: any, message: string, status = 400) {
  return c.json({ success: false, error: message }, status);
}

function isPublisher(role: string) {
  return role === 'Admin' || role === 'Cluster_Head';
}

function notificationScope(user: Variables['user']) {
  if (user.role === 'Admin') return { sql: `n.scope_type IN ('system', 'cluster', 'school')`, params: [] as string[] };
  if (user.role === 'Cluster_Head') {
    return {
      sql: `(n.scope_type = 'system' OR (n.scope_type = 'cluster' AND n.scope_id = ?) OR (n.scope_type = 'school' AND EXISTS (SELECT 1 FROM schools ns WHERE ns.udise_code = n.scope_id AND ns.cluster_code = ? AND ns.is_active = 1)))`,
      params: [user.cluster_code || '', user.cluster_code || '']
    };
  }
  return {
    sql: `(n.scope_type = 'system' OR (n.scope_type = 'school' AND n.scope_id = ?))`,
    params: [user.school_code || '']
  };
}

function audienceScope(user: Variables['user'], scopeType: string, scopeId: string | null) {
  if (scopeType === 'system') return { sql: `1 = 1`, params: [] as string[] };
  if (!scopeId) return null;
  if (scopeType === 'cluster') return { sql: `u.cluster_code = ?`, params: [scopeId] };
  if (scopeType === 'school') return { sql: `u.school_code = ?`, params: [scopeId] };
  return null;
}

notificationRouter.use('*', authMiddleware());

notificationRouter.post('/', async (c) => {
  const actor = c.get('user');
  if (!isPublisher(actor.role)) return error(c, 'Only App Admin or Cluster Head can create notifications', 403);
  try {
    const body = await c.req.json().catch(() => ({}));
    const title = typeof body.title === 'string' ? body.title.trim() : '';
    const content = typeof body.content === 'string' ? body.content.trim() : '';
    const scopeType = typeof body.scope_type === 'string' ? body.scope_type.trim() : '';
    const scopeId = body.scope_id == null ? null : String(body.scope_id).trim();
    if (!title) return error(c, 'Notification title is required');
    if (!content) return error(c, 'Notification content is required');
    if (!scopeType) return error(c, 'Notification scope_type is required');
    if (!['system', 'cluster', 'school'].includes(scopeType)) return error(c, 'Invalid notification scope_type');
    if (scopeType !== 'system' && !scopeId) return error(c, 'Notification scope_id is required for cluster or school scope');
    if (title.length > 200) return error(c, 'Notification title is too long');
    if (content.length > 5000) return error(c, 'Notification content is too long');
    if (actor.role === 'Cluster_Head' && scopeType === 'cluster' && scopeId !== actor.cluster_code) return error(c, 'Cluster Head can only target their own cluster', 403);
    if (actor.role === 'Cluster_Head' && scopeType === 'school') {
      const school = await c.env.DB.prepare(`SELECT id FROM schools WHERE udise_code = ? AND cluster_code = ? AND is_active = 1 LIMIT 1`).bind(scopeId, actor.cluster_code || '').first();
      if (!school) return error(c, 'Cluster Head can only target a school in their cluster', 403);
    }

    const id = crypto.randomUUID();
    await c.env.DB.prepare(`
      INSERT INTO notifications (
        id, title, content, scope_type, scope_id,
        publisher_id, publisher_name, publisher_role, status,
        attachment_key, attachment_name, attachment_mime_type,
        attachment_size, attachment_type
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'draft', ?, ?, ?, ?, ?)
    `).bind(
      id, title, content, scopeType, scopeId, actor.id, actor.name || 'Unknown User', actor.role,
      body.attachment_key == null ? null : String(body.attachment_key),
      body.attachment_name == null ? null : String(body.attachment_name),
      body.attachment_mime_type == null ? null : String(body.attachment_mime_type),
      body.attachment_size == null ? null : Number(body.attachment_size),
      body.attachment_type == null ? null : String(body.attachment_type)
    ).run();
    return c.json({ success: true, data: { id, status: 'draft' } }, 201);
  } catch (e: any) {
    console.error('NOTIFICATION_CREATE_ERROR:', e);
    return error(c, 'Unable to create notification', 500);
  }
});

notificationRouter.post('/:id/publish', async (c) => {
  const actor = c.get('user');
  if (!isPublisher(actor.role)) return error(c, 'Only App Admin or Cluster Head can publish notifications', 403);
  const notificationId = c.req.param('id');
  try {
    const notification = await c.env.DB.prepare(`
      SELECT id, status, scope_type, scope_id FROM notifications
      WHERE id = ? AND is_deleted = 0 LIMIT 1
    `).bind(notificationId).first<any>();
    if (!notification) return error(c, 'Notification not found', 404);
    if (notification.status === 'published') return error(c, 'Notification is already published', 409);
    if (notification.status === 'deleted') return error(c, 'Deleted notification cannot be published', 409);
    if (actor.role === 'Cluster_Head') {
      if (notification.scope_type === 'cluster' && notification.scope_id !== actor.cluster_code) return error(c, 'Cluster Head can only publish notifications for their cluster', 403);
      if (notification.scope_type === 'school') {
        const school = await c.env.DB.prepare(`SELECT id FROM schools WHERE udise_code = ? AND cluster_code = ? AND is_active = 1 LIMIT 1`).bind(notification.scope_id, actor.cluster_code || '').first();
        if (!school) return error(c, 'Cluster Head can only publish a school notification in their cluster', 403);
      }
    }
    await c.env.DB.prepare(`
      UPDATE notifications
      SET status = 'published', published_at = CURRENT_TIMESTAMP,
          published_by = ?, updated_at = CURRENT_TIMESTAMP
      WHERE id = ? AND status = 'draft' AND is_deleted = 0
    `).bind(actor.id, notificationId).run();
    return c.json({ success: true, data: { id: notificationId, status: 'published', published_by: actor.id } });
  } catch (e: any) {
    console.error('NOTIFICATION_PUBLISH_ERROR:', e);
    return error(c, 'Unable to publish notification', 500);
  }
});

notificationRouter.get('/', async (c) => {
  try {
    const limit = Math.min(Math.max(Number(c.req.query('limit') || 50), 1), 100);
    const requestedStatus = c.req.query('status') || 'published';
    if (!['published', 'draft'].includes(requestedStatus)) return error(c, 'Invalid notification status');
    const user = c.get('user');
    if (requestedStatus === 'draft' && !isPublisher(user.role)) return error(c, 'Only App Admin or Cluster Head can view drafts', 403);
    const scope = notificationScope(user);
    const result = await c.env.DB.prepare(`
      SELECT n.id, n.title, n.content, n.scope_type, n.scope_id,
        n.publisher_id, n.publisher_name, n.publisher_role, n.status,
        n.created_at, n.updated_at, n.published_at, n.published_by,
        n.attachment_key, n.attachment_name, n.attachment_mime_type,
        n.attachment_size, n.attachment_type,
        CASE WHEN nr.id IS NULL THEN 0 ELSE 1 END AS is_read,
        nr.read_at
      FROM notifications n
      LEFT JOIN notification_reads nr
        ON nr.notification_id = n.id AND nr.user_id = ?
      WHERE n.status = ? AND n.is_deleted = 0 AND ${requestedStatus === 'published' ? scope.sql : `n.publisher_id = ?`}
      ORDER BY COALESCE(n.published_at, n.created_at) DESC
      LIMIT ?
    `).bind(
      ...(requestedStatus === 'published' ? [user.id, requestedStatus, ...scope.params, limit] : [user.id, requestedStatus, user.id, limit])
    ).all();
    return c.json({ success: true, data: result.results || [] });
  } catch (e: any) {
    console.error('NOTIFICATION_LIST_ERROR:', e);
    return error(c, 'Unable to load notifications', 500);
  }
});

notificationRouter.get('/unread-count', async (c) => {
  try {
    const user = c.get('user');
    const scope = notificationScope(user);
    const row = await c.env.DB.prepare(`
      SELECT COUNT(*) AS count
      FROM notifications n
      LEFT JOIN notification_reads nr ON nr.notification_id = n.id AND nr.user_id = ?
      WHERE n.status = 'published' AND n.is_deleted = 0 AND nr.id IS NULL AND ${scope.sql}
    `).bind(user.id, ...scope.params).first<{ count: number }>();
    return c.json({ success: true, data: { count: Number(row?.count || 0) } });
  } catch (e: any) {
    console.error('NOTIFICATION_UNREAD_COUNT_ERROR:', e);
    return error(c, 'Unable to load notification count', 500);
  }
});

notificationRouter.post('/:id/read', async (c) => {
  const user = c.get('user');
  const notificationId = c.req.param('id');
  try {
    const scope = notificationScope(user);
    const notification = await c.env.DB.prepare(`
      SELECT n.id FROM notifications n
      WHERE n.id = ? AND n.status = 'published' AND n.is_deleted = 0 AND ${scope.sql} LIMIT 1
    `).bind(notificationId, ...scope.params).first<{ id: string }>();
    if (!notification) return error(c, 'Notification not found', 404);
    await c.env.DB.prepare(`
      INSERT INTO notification_reads (id, notification_id, user_id, read_at)
      VALUES (?, ?, ?, CURRENT_TIMESTAMP)
      ON CONFLICT(notification_id, user_id) DO UPDATE SET read_at = CURRENT_TIMESTAMP
    `).bind(crypto.randomUUID(), notificationId, user.id).run();
    return c.json({ success: true, data: { notification_id: notificationId, read: true } });
  } catch (e: any) {
    console.error('NOTIFICATION_READ_ERROR:', e);
    return error(c, 'Unable to mark notification as read', 500);
  }
});

notificationRouter.get('/:id/audience', async (c) => {
  const user = c.get('user');
  if (!isPublisher(user.role)) return error(c, 'Only App Admin or Cluster Head can view notification audience', 403);
  const notificationId = c.req.param('id');
  const requestedStatus = c.req.query('status') || 'read';
  if (!['read', 'unread'].includes(requestedStatus)) return error(c, 'Invalid audience status');
  try {
    const notification = await c.env.DB.prepare(`
      SELECT id, scope_type, scope_id, publisher_id
      FROM notifications WHERE id = ? AND status = 'published' AND is_deleted = 0 LIMIT 1
    `).bind(notificationId).first<any>();
    if (!notification) return error(c, 'Notification not found', 404);
    if (user.role === 'Cluster_Head') {
      if (notification.scope_type === 'system') return error(c, 'Audience controls are not available for system notifications to Cluster Head', 403);
      if (notification.scope_type === 'cluster' && notification.scope_id !== user.cluster_code) return error(c, 'Notification is outside your cluster', 403);
      if (notification.scope_type === 'school') {
        const school = await c.env.DB.prepare(`SELECT id FROM schools WHERE udise_code = ? AND cluster_code = ? AND is_active = 1 LIMIT 1`).bind(notification.scope_id, user.cluster_code || '').first();
        if (!school) return error(c, 'Notification is outside your cluster', 403);
      }
    }
    const scope = audienceScope(user, notification.scope_type, notification.scope_id);
    if (!scope) return error(c, 'Invalid notification audience scope', 400);
    const readJoin = requestedStatus === 'read'
      ? `JOIN notification_reads nr ON nr.notification_id = ? AND nr.user_id = u.id`
      : `LEFT JOIN notification_reads nr ON nr.notification_id = ? AND nr.user_id = u.id`;
    const readWhere = requestedStatus === 'read' ? `AND nr.notification_id IS NOT NULL` : `AND nr.notification_id IS NULL`;
    const result = await c.env.DB.prepare(`
      SELECT u.id, u.name, u.role,
        COALESCE(NULLIF(u.school_name, ''), s.school_name, '') AS school_name,
        nr.read_at
      FROM users u
      LEFT JOIN schools s ON s.udise_code = u.school_code
      ${readJoin}
      WHERE u.status = 'Active' AND ${scope.sql} ${readWhere}
      ORDER BY u.name COLLATE NOCASE ASC
    `).bind(notificationId, ...scope.params).all();
    return c.json({ success: true, data: result.results || [] });
  } catch (e: any) {
    console.error('NOTIFICATION_AUDIENCE_ERROR:', e);
    return error(c, 'Unable to load notification audience', 500);
  }
});
