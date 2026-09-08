import { Hono } from 'hono';
import { authMiddleware } from '../middleware/auth';
import { Bindings, Variables } from '../types';

export const excelRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();
const MAX_EXCEL_BYTES = 50 * 1024 * 1024;
const REPORT_MIME_TYPES: Record<string, string> = {
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'excel',
  'application/vnd.ms-excel': 'excel',
  'text/csv': 'excel',
  'application/pdf': 'pdf'
};

function error(c: any, message: string, status = 400) { return c.json({ success: false, error: message }, status); }

async function getFile(db: D1Database, groupId: string, messageId: string) {
  return db.prepare(`SELECT id, group_id, group_name, sender_id, sender_name, attachment_key, file_name, mime_type, file_size, excel_status, excel_version, excel_published_at, excel_published_by, created_at, updated_at FROM messages WHERE id = ? AND group_id = ? AND message_type IN ('excel', 'pdf') AND is_deleted = 0 LIMIT 1`).bind(messageId, groupId).first<any>();
}

async function groupAccess(db: D1Database, actor: any, groupId: string) {
  const group = await db.prepare('SELECT id, scope_type, cluster_code, created_by, is_active FROM groups WHERE id = ? LIMIT 1').bind(groupId).first<any>();
  if (!group || group.is_active !== 1) return { group: null, allowed: false };
  if (actor.role === 'Admin') return { group, allowed: true };
  if (actor.role === 'Cluster_Head') return { group, allowed: group.scope_type === 'cluster' && group.cluster_code === actor.cluster_code };
  const member = await db.prepare('SELECT id FROM group_members WHERE group_id = ? AND user_id = ? AND is_active = 1 LIMIT 1').bind(groupId, actor.id).first();
  return { group, allowed: !!member };
}

excelRouter.use('*', authMiddleware());

// Upload a report directly from the Reports tab. The report is published immediately.
// The selected group supplies the existing messages.group_id relationship and scope context.
excelRouter.post('/reports/upload', async (c) => {
  const actor = c.get('user');
  if (actor.role !== 'Admin' && actor.role !== 'Cluster_Head') return error(c, 'Only App Admin or Cluster Head can upload Reports', 403);
  try {
    const form = await c.req.formData();
    const groupId = String(form.get('group_id') || '').trim();
    const uploaded = form.get('file');
    if (!groupId) return error(c, 'Report group is required');
    if (!(uploaded instanceof File)) return error(c, 'Report file is required');
    if (uploaded.size <= 0) return error(c, 'Report file is empty');
    if (uploaded.size > MAX_EXCEL_BYTES) return error(c, 'Report file must be 50 MB or smaller');
    const mimeType = (uploaded.type || '').split(';')[0].trim().toLowerCase();
    const messageType = REPORT_MIME_TYPES[mimeType];
    if (!messageType) return error(c, 'Only Excel, CSV and PDF Reports are supported');

    const access = await groupAccess(c.env.DB, actor, groupId);
    if (!access.group || !access.allowed) return error(c, 'You do not have access to this group', 403);

    const messageId = crypto.randomUUID();
    const originalName = uploaded.name?.trim() || (messageType === 'pdf' ? 'report.pdf' : 'report.xlsx');
    const safeName = originalName.replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 240) || (messageType === 'pdf' ? 'report.pdf' : 'report.xlsx');
    const key = `groups/${groupId}/reports/${messageId}-${safeName}`;
    await c.env.R2_BUCKET.put(key, uploaded.stream(), {
      httpMetadata: { contentType: mimeType, contentDisposition: `attachment; filename="${safeName}"` },
      customMetadata: { groupId, uploadedBy: actor.id, messageId, messageType, report: 'published' }
    });

    await c.env.DB.prepare(`INSERT INTO messages (id, group_id, group_name, sender_id, sender_name, content, media_url, message_type, attachment_key, file_name, mime_type, file_size, is_deleted, is_read, excel_status, excel_version, excel_published_at, excel_published_by) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, 0, 0, 'published', 1, CURRENT_TIMESTAMP, ?)`).bind(messageId, groupId, access.group.group_name || '', actor.id, actor.name || '', safeName, messageType, key, safeName, mimeType, uploaded.size, actor.id).run();

    return c.json({ success: true, data: { id: messageId, group_id: groupId, group_name: access.group.group_name || '', sender_name: actor.name || '', file_name: safeName, mime_type: mimeType, file_size: uploaded.size, excel_version: 1, excel_status: 'published', excel_published_by: actor.id } }, 201);
  } catch (e: any) {
    console.error('Report upload failed:', e);
    return error(c, 'Unable to upload Report', 500);
  }
});

// Save the current workbook bytes back to the same R2 object. During group collection,
// every active member may save; after publication only Admin/Cluster Head may save.
excelRouter.put('/:groupId/:messageId', async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('groupId');
  const messageId = c.req.param('messageId');
  const access = await groupAccess(c.env.DB, actor, groupId);
  if (!access.group || !access.allowed) return error(c, 'You do not have access to this group', 403);
  const file = await getFile(c.env.DB, groupId, messageId);
  if (!file?.attachment_key) return error(c, 'Excel file not found', 404);
  const isPublished = file.excel_status === 'published';
  if (isPublished && actor.role !== 'Admin' && actor.role !== 'Cluster_Head') return error(c, 'This Excel is published and can only be edited by App Admin or Cluster Head', 403);
  if (!c.req.raw.body) return error(c, 'Workbook body is empty');
  const contentLength = Number(c.req.header('Content-Length') || 0);
  if (contentLength > MAX_EXCEL_BYTES) return error(c, 'Excel file must be 50 MB or smaller');
  const mimeType = (c.req.header('Content-Type') || file.mime_type || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet').split(';')[0].trim();
  const fileName = file.file_name || 'data.xlsx';
  await c.env.R2_BUCKET.put(file.attachment_key, c.req.raw.body, {
    httpMetadata: { contentType: mimeType, contentDisposition: `attachment; filename="${fileName.replace(/[^a-zA-Z0-9._-]/g, '_')}"` },
    customMetadata: { groupId, updatedBy: actor.id, messageId, messageType: 'excel' }
  });
  const object = await c.env.R2_BUCKET.head(file.attachment_key);
  const nextVersion = Number(file.excel_version || 1) + 1;
  await c.env.DB.prepare('UPDATE messages SET excel_version = ?, file_size = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND group_id = ?').bind(nextVersion, object?.size || contentLength || null, messageId, groupId).run();
  return c.json({ success: true, data: { version: nextVersion, file_size: object?.size || contentLength || null, status: file.excel_status } });
});

// Publish a group workbook to the Reports tab. Publishing freezes normal group editing.
excelRouter.post('/:groupId/:messageId/publish', async (c) => {
  const actor = c.get('user');
  if (actor.role !== 'Admin' && actor.role !== 'Cluster_Head') return error(c, 'Only App Admin or Cluster Head can send Excel to Reports', 403);
  const groupId = c.req.param('groupId');
  const messageId = c.req.param('messageId');
  const access = await groupAccess(c.env.DB, actor, groupId);
  if (!access.group || !access.allowed) return error(c, 'You do not have access to this group', 403);
  const file = await getFile(c.env.DB, groupId, messageId);
  if (!file?.attachment_key) return error(c, 'Excel file not found', 404);
  await c.env.DB.prepare(`UPDATE messages SET excel_status = 'published', excel_published_at = CURRENT_TIMESTAMP, excel_published_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND group_id = ?`).bind(actor.id, messageId, groupId).run();
  return c.json({ success: true, data: { status: 'published', published_at: new Date().toISOString(), published_by: actor.id } });
});

// Reports are intentionally global after publication: every registered user can access them.
excelRouter.get('/reports', async (c) => {
  const result = await c.env.DB.prepare(`SELECT m.id, m.group_id, m.group_name, m.sender_name, m.attachment_key, m.file_name, m.mime_type, m.file_size, m.excel_version, m.excel_published_at, m.excel_published_by, m.created_at, m.updated_at FROM messages m WHERE m.message_type IN ('excel', 'pdf') AND m.excel_status = 'published' AND m.is_deleted = 0 ORDER BY COALESCE(m.excel_published_at, m.created_at) DESC LIMIT 200`).all();
  return c.json({ success: true, data: result.results || [] });
});

excelRouter.get('/:groupId/:messageId/status', async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('groupId');
  const messageId = c.req.param('messageId');
  const access = await groupAccess(c.env.DB, actor, groupId);
  if (!access.group || !access.allowed) return error(c, 'You do not have access to this group', 403);
  const file = await getFile(c.env.DB, groupId, messageId);
  if (!file) return error(c, 'Excel file not found', 404);
  return c.json({ success: true, data: { status: file.excel_status, version: Number(file.excel_version || 1), published_at: file.excel_published_at, published_by: file.excel_published_by } });
});
