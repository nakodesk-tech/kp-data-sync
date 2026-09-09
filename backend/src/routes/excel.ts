import { Hono } from 'hono';
import { authMiddleware } from '../middleware/auth';
import { Bindings, Variables } from '../types';

export const excelRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();
const MAX_EXCEL_BYTES = 50 * 1024 * 1024;

function error(c: any, message: string, status = 400) { return c.json({ success: false, error: message }, status); }

async function getFile(db: D1Database, groupId: string, messageId: string) {
  return db.prepare(`SELECT id, group_id, group_name, sender_id, sender_name, attachment_key, file_name, mime_type, file_size, excel_status, excel_version, excel_published_at, excel_published_by, created_at, updated_at FROM messages WHERE id = ? AND group_id = ? AND message_type IN ('excel', 'pdf') AND is_deleted = 0 LIMIT 1`).bind(messageId, groupId).first<any>();
}

async function groupAccess(db: D1Database, actor: any, groupId: string) {
  const group = await db.prepare('SELECT id, group_name, scope_type, cluster_code, created_by, is_active FROM groups WHERE id = ? LIMIT 1').bind(groupId).first<any>();
  if (!group || group.is_active !== 1) return { group: null, allowed: false };
  if (actor.role === 'Admin') return { group, allowed: true };
  if (actor.role === 'Cluster_Head') return { group, allowed: group.scope_type === 'cluster' && group.cluster_code === actor.cluster_code };
  const member = await db.prepare('SELECT id FROM group_members WHERE group_id = ? AND user_id = ? AND is_active = 1 LIMIT 1').bind(groupId, actor.id).first();
  return { group, allowed: !!member };
}

excelRouter.use('*', authMiddleware());

// Published files are readable from Reports by authenticated users.
excelRouter.get('/reports/:messageId/download', async (c) => {
  const messageId = c.req.param('messageId');
  const file = await c.env.DB.prepare(`SELECT attachment_key, file_name, mime_type FROM messages WHERE id = ? AND message_type IN ('excel', 'pdf') AND excel_status = 'published' AND is_deleted = 0 LIMIT 1`).bind(messageId).first<any>();
  if (!file?.attachment_key) return error(c, 'Published Report not found', 404);
  const object = await c.env.R2_BUCKET.get(file.attachment_key);
  if (!object) return error(c, 'Report file not found', 404);
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set('Cache-Control', 'private, max-age=3600');
  if (file.file_name) headers.set('Content-Disposition', `attachment; filename="${String(file.file_name).replace(/[^a-zA-Z0-9._-]/g, '_')}"`);
  if (file.mime_type) headers.set('Content-Type', file.mime_type);
  return new Response(object.body, { status: 200, headers });
});

// Save a shared Excel workbook back to the same R2 object. Before publish,
// group members may edit it. After publish, only App Admin / Cluster Head may edit.
excelRouter.put('/:groupId/:messageId', async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('groupId');
  const messageId = c.req.param('messageId');
  const access = await groupAccess(c.env.DB, actor, groupId);
  if (!access.group || !access.allowed) return error(c, 'You do not have access to this group', 403);
  const file = await getFile(c.env.DB, groupId, messageId);
  if (!file?.attachment_key) return error(c, 'Shared file not found', 404);
  const isPublished = file.excel_status === 'published';
  if (isPublished && actor.role !== 'Admin' && actor.role !== 'Cluster_Head') return error(c, 'This file is published and can only be edited by App Admin or Cluster Head', 403);
  if (!c.req.raw.body) return error(c, 'File body is empty');
  const contentLength = Number(c.req.header('Content-Length') || 0);
  if (contentLength > MAX_EXCEL_BYTES) return error(c, 'File must be 50 MB or smaller');
  const mimeType = (c.req.header('Content-Type') || file.mime_type || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet').split(';')[0].trim();
  const fileName = file.file_name || 'data.xlsx';
  await c.env.R2_BUCKET.put(file.attachment_key, c.req.raw.body, {
    httpMetadata: { contentType: mimeType, contentDisposition: `attachment; filename="${fileName.replace(/[^a-zA-Z0-9._-]/g, '_')}"` },
    customMetadata: { groupId, updatedBy: actor.id, messageId, messageType: file.mime_type === 'application/pdf' ? 'pdf' : 'excel' }
  });
  const object = await c.env.R2_BUCKET.head(file.attachment_key);
  const nextVersion = Number(file.excel_version || 1) + 1;
  await c.env.DB.prepare('UPDATE messages SET excel_version = ?, file_size = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND group_id = ?').bind(nextVersion, object?.size || contentLength || null, messageId, groupId).run();
  return c.json({ success: true, data: { version: nextVersion, file_size: object?.size || contentLength || null, status: file.excel_status } });
});

// Only App Admin and Cluster Head can publish a file from the Group Chat into Reports.
excelRouter.post('/:groupId/:messageId/publish', async (c) => {
  const actor = c.get('user');
  if (actor.role !== 'Admin' && actor.role !== 'Cluster_Head') return error(c, 'Only App Admin or Cluster Head can publish files to Reports', 403);
  const groupId = c.req.param('groupId');
  const messageId = c.req.param('messageId');
  const access = await groupAccess(c.env.DB, actor, groupId);
  if (!access.group || !access.allowed) return error(c, 'You do not have access to this group', 403);
  const file = await getFile(c.env.DB, groupId, messageId);
  if (!file?.attachment_key) return error(c, 'Shared file not found', 404);
  await c.env.DB.prepare(`UPDATE messages SET excel_status = 'published', excel_published_at = CURRENT_TIMESTAMP, excel_published_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND group_id = ?`).bind(actor.id, messageId, groupId).run();
  return c.json({ success: true, data: { status: 'published', published_at: new Date().toISOString(), published_by: actor.id } });
});

excelRouter.get('/reports', async (c) => {
  const result = await c.env.DB.prepare(`SELECT m.id, m.group_id, m.group_name, m.sender_name, m.attachment_key, m.file_name, m.mime_type, m.file_size, m.excel_version, m.excel_published_at, m.excel_published_by, m.created_at, m.updated_at, COALESCE(u.role, '') AS publisher_role FROM messages m LEFT JOIN users u ON u.id = m.excel_published_by WHERE m.message_type IN ('excel', 'pdf') AND m.excel_status = 'published' AND m.is_deleted = 0 ORDER BY COALESCE(m.excel_published_at, m.created_at) DESC LIMIT 200`).all();
  return c.json({ success: true, data: result.results || [] });
});

excelRouter.delete('/reports/:messageId', async (c) => {
  const actor = c.get('user');
  if (actor.role !== 'Admin' && actor.role !== 'Cluster_Head') return error(c, 'Only App Admin or Cluster Head can delete Reports', 403);
  const messageId = c.req.param('messageId');
  const file = await c.env.DB.prepare(`SELECT id, attachment_key, group_id FROM messages WHERE id = ? AND message_type IN ('excel', 'pdf') AND excel_status = 'published' AND is_deleted = 0 LIMIT 1`).bind(messageId).first<any>();
  if (!file) return error(c, 'Published Report not found', 404);
  const access = await groupAccess(c.env.DB, actor, file.group_id);
  if (!access.group || !access.allowed) return error(c, 'You do not have access to this group', 403);
  if (file.attachment_key) await c.env.R2_BUCKET.delete(file.attachment_key);
  await c.env.DB.prepare(`UPDATE messages SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?`).bind(messageId).run();
  return c.json({ success: true, data: { id: messageId, deleted: true } });
});

excelRouter.get('/:groupId/:messageId/status', async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('groupId');
  const messageId = c.req.param('messageId');
  const access = await groupAccess(c.env.DB, actor, groupId);
  if (!access.group || !access.allowed) return error(c, 'You do not have access to this group', 403);
  const file = await getFile(c.env.DB, groupId, messageId);
  if (!file) return error(c, 'Shared file not found', 404);
  return c.json({ success: true, data: { status: file.excel_status, version: Number(file.excel_version || 1), published_at: file.excel_published_at, published_by: file.excel_published_by } });
});
