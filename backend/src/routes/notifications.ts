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
    if (title.length > 200) return error(c, 'Notification title is too long');
    if (content.length > 5000) return error(c, 'Notification content is too long');

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
      SELECT id, status FROM notifications
      WHERE id = ? AND is_deleted = 0 LIMIT 1
    `).bind(notificationId).first<any>();
    if (!notification) return error(c, 'Notification not found', 404);
    if (notification.status === 'published') return error(c, 'Notification is already published', 409);
    if (notification.status === 'deleted') return error(c, 'Deleted notification cannot be published', 409);
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

// Scope filtering will be added in the dedicated scope phase.
notificationRouter.get('/', async (c) => {
  try {
    const limit = Math.min(Math.max(Number(c.req.query('limit') || 50), 1), 100);
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
      WHERE n.status = 'published' AND n.is_deleted = 0
      ORDER BY COALESCE(n.published_at, n.created_at) DESC
      LIMIT ?
    `).bind(c.get('user').id, limit).all();
    return c.json({ success: true, data: result.results || [] });
  } catch (e: any) {
    console.error('NOTIFICATION_LIST_ERROR:', e);
    return error(c, 'Unable to load notifications', 500);
  }
});

notificationRouter.get('/unread-count', async (c) => {
  try {
    const row = await c.env.DB.prepare(`
      SELECT COUNT(*) AS count
      FROM notifications n
      LEFT JOIN notification_reads nr ON nr.notification_id = n.id AND nr.user_id = ?
      WHERE n.status = 'published' AND n.is_deleted = 0 AND nr.id IS NULL
    `).bind(c.get('user').id).first<{ count: number }>();
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
    const notification = await c.env.DB.prepare(`
      SELECT id FROM notifications WHERE id = ? AND status = 'published' AND is_deleted = 0 LIMIT 1
    `).bind(notificationId).first<{ id: string }>();
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

notificationRouter.post('/:id/dismiss', async (c) => {
  const user = c.get('user');
  const notificationId = c.req.param('id');
  try {
    const notification = await c.env.DB.prepare(`
      SELECT id FROM notifications WHERE id = ? AND status = 'published' AND is_deleted = 0 LIMIT 1
    `).bind(notificationId).first<{ id: string }>();
    if (!notification) return error(c, 'Notification not found', 404);
    await c.env.DB.prepare(`
      INSERT INTO notification_reads (id, notification_id, user_id, read_at)
      VALUES (?, ?, ?, CURRENT_TIMESTAMP)
      ON CONFLICT(notification_id, user_id) DO UPDATE SET read_at = CURRENT_TIMESTAMP
    `).bind(crypto.randomUUID(), notificationId, user.id).run();
    return c.json({ success: true, data: { notification_id: notificationId, dismissed: true } });
  } catch (e: any) {
    console.error('NOTIFICATION_DISMISS_ERROR:', e);
    return error(c, 'Unable to dismiss notification', 500);
  }
});
