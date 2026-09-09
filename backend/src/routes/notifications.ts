import { Hono } from 'hono';
import { authMiddleware } from '../middleware/auth';
import { Bindings, Variables } from '../types';

export const notificationRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

function error(c: any, message: string, status = 400) {
  return c.json({ success: false, error: message }, status);
}

notificationRouter.use('*', authMiddleware());

// List notifications visible to the authenticated user.
// Scope filtering is intentionally limited in this phase; recipient/scope rules
// will be tightened when notification delivery scope is implemented.
notificationRouter.get('/', async (c) => {
  try {
    const limit = Math.min(Math.max(Number(c.req.query('limit') || 50), 1), 100);
    const result = await c.env.DB.prepare(`
      SELECT
        n.id, n.title, n.content, n.scope_type, n.scope_id,
        n.publisher_id, n.publisher_name, n.publisher_role,
        n.status, n.created_at, n.updated_at, n.published_at,
        n.published_by, n.attachment_key, n.attachment_name,
        n.attachment_mime_type, n.attachment_size, n.attachment_type,
        CASE WHEN nr.id IS NULL THEN 0 ELSE 1 END AS is_read,
        nr.read_at
      FROM notifications n
      LEFT JOIN notification_reads nr
        ON nr.notification_id = n.id AND nr.user_id = ?
      WHERE n.status = 'published' AND n.is_deleted = 0
        AND (n.expires_at IS NULL OR n.expires_at > CURRENT_TIMESTAMP)
      ORDER BY COALESCE(n.published_at, n.created_at) DESC
      LIMIT ?
    `).bind(c.get('user').id, limit).all();

    return c.json({ success: true, data: result.results || [] });
  } catch (e: any) {
    console.error('NOTIFICATION_LIST_ERROR:', e);
    return error(c, 'Unable to load notifications', 500);
  }
});

// Return unread count for the authenticated user.
notificationRouter.get('/unread-count', async (c) => {
  try {
    const row = await c.env.DB.prepare(`
      SELECT COUNT(*) AS count
      FROM notifications n
      LEFT JOIN notification_reads nr
        ON nr.notification_id = n.id AND nr.user_id = ?
      WHERE n.status = 'published' AND n.is_deleted = 0
        AND nr.id IS NULL
        AND (n.expires_at IS NULL OR n.expires_at > CURRENT_TIMESTAMP)
    `).bind(c.get('user').id).first<{ count: number }>();

    return c.json({ success: true, data: { count: Number(row?.count || 0) } });
  } catch (e: any) {
    console.error('NOTIFICATION_UNREAD_COUNT_ERROR:', e);
    return error(c, 'Unable to load notification count', 500);
  }
});

// Mark one notification as read. Idempotent through UNIQUE(notification_id,user_id).
notificationRouter.post('/:id/read', async (c) => {
  const user = c.get('user');
  const notificationId = c.req.param('id');
  try {
    const notification = await c.env.DB.prepare(`
      SELECT id FROM notifications
      WHERE id = ? AND status = 'published' AND is_deleted = 0
      LIMIT 1
    `).bind(notificationId).first<{ id: string }>();
    if (!notification) return error(c, 'Notification not found', 404);

    await c.env.DB.prepare(`
      INSERT INTO notification_reads (id, notification_id, user_id, read_at)
      VALUES (?, ?, ?, CURRENT_TIMESTAMP)
      ON CONFLICT(notification_id, user_id)
      DO UPDATE SET read_at = CURRENT_TIMESTAMP
    `).bind(crypto.randomUUID(), notificationId, user.id).run();

    return c.json({ success: true, data: { notification_id: notificationId, read: true } });
  } catch (e: any) {
    console.error('NOTIFICATION_READ_ERROR:', e);
    return error(c, 'Unable to mark notification as read', 500);
  }
});

// Dismiss is represented by a read marker in this phase. A separate per-user
// dismissal model can be introduced later without changing notification content.
notificationRouter.post('/:id/dismiss', async (c) => {
  const user = c.get('user');
  const notificationId = c.req.param('id');
  try {
    const notification = await c.env.DB.prepare(`
      SELECT id FROM notifications
      WHERE id = ? AND status = 'published' AND is_deleted = 0
      LIMIT 1
    `).bind(notificationId).first<{ id: string }>();
    if (!notification) return error(c, 'Notification not found', 404);

    await c.env.DB.prepare(`
      INSERT INTO notification_reads (id, notification_id, user_id, read_at)
      VALUES (?, ?, ?, CURRENT_TIMESTAMP)
      ON CONFLICT(notification_id, user_id)
      DO UPDATE SET read_at = CURRENT_TIMESTAMP
    `).bind(crypto.randomUUID(), notificationId, user.id).run();

    return c.json({ success: true, data: { notification_id: notificationId, dismissed: true } });
  } catch (e: any) {
    console.error('NOTIFICATION_DISMISS_ERROR:', e);
    return error(c, 'Unable to dismiss notification', 500);
  }
});
