import { Hono } from 'hono';
import { authMiddleware } from '../middleware/auth';
import { Bindings, Variables } from '../types';

export const messageRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

const MESSAGE_TYPES = new Set(['text', 'image', 'excel', 'pdf', 'audio', 'link']);
const MAX_HISTORY = 100;
const MAX_ATTACHMENT_BYTES = 50 * 1024 * 1024;

const ALLOWED_MEDIA: Record<string, Set<string>> = {
  image: new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']),
  excel: new Set(['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'application/vnd.ms-excel', 'text/csv']),
  pdf: new Set(['application/pdf']),
  audio: new Set(['audio/mpeg', 'audio/mp4', 'audio/aac', 'audio/wav', 'audio/ogg', 'audio/webm', 'audio/x-m4a'])
};

function error(c: any, message: string, status = 400) { return c.json({ success: false, error: message }, status); }

async function groupAccess(db: D1Database, actor: any, groupId: string) {
  const group = await db.prepare('SELECT id, group_name, group_type, scope_type, cluster_code, school_code, created_by, is_active FROM groups WHERE id = ? LIMIT 1').bind(groupId).first<any>();
  if (!group || group.is_active !== 1) return { group: null, allowed: false };
  if (actor.role === 'Admin') return { group, allowed: true };
  if (actor.role === 'Cluster_Head') return { group, allowed: group.scope_type === 'cluster' && group.cluster_code === actor.cluster_code };
  const member = await db.prepare('SELECT id FROM group_members WHERE group_id = ? AND user_id = ? AND is_active = 1 LIMIT 1').bind(groupId, actor.id).first();
  return { group, allowed: !!member };
}

messageRouter.use('*', authMiddleware());

messageRouter.get('/:id', async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('id');
  const access = await groupAccess(c.env.DB, actor, groupId);
  if (!access.group) return error(c, 'Group not found', 404);
  if (!access.allowed) return error(c, 'You do not have access to this group', 403);
  const url = new URL(c.req.url);
  const requestedLimit = Number(url.searchParams.get('limit') || 50);
  const limit = Math.min(Math.max(Number.isFinite(requestedLimit) ? Math.floor(requestedLimit) : 50, 1), MAX_HISTORY);
  const before = url.searchParams.get('before');
  const columns = 'id, group_id, group_name, sender_id, sender_name, content, media_url, message_type, attachment_key, file_name, mime_type, file_size, link_url, client_message_id, is_deleted, is_read, created_at, updated_at, excel_status, excel_version, excel_published_at, excel_published_by';
  const result = before
    ? await c.env.DB.prepare(`SELECT ${columns} FROM messages WHERE group_id = ? AND created_at < ? ORDER BY created_at DESC LIMIT ?`).bind(groupId, before, limit).all()
    : await c.env.DB.prepare(`SELECT ${columns} FROM messages WHERE group_id = ? ORDER BY created_at DESC LIMIT ?`).bind(groupId, limit).all();
  return c.json({ success: true, data: (result.results || []).reverse() });
});

messageRouter.delete('/:id/:messageId', async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('id');
  const messageId = c.req.param('messageId');
  const access = await groupAccess(c.env.DB, actor, groupId);
  if (!access.group) return error(c, 'Group not found', 404);
  if (!access.allowed) return error(c, 'You do not have access to this group', 403);
  try {
    const message = await c.env.DB.prepare('SELECT id, sender_id, attachment_key, is_deleted FROM messages WHERE id = ? AND group_id = ? LIMIT 1').bind(messageId, groupId).first<any>();
    if (!message) return error(c, 'Message not found', 404);
    const owner = access.group.created_by === actor.id;
    if (!owner && message.sender_id !== actor.id) return error(c, 'You can delete only your own messages', 403);
    if (message.is_deleted === 1) return c.json({ success: true });
    await c.env.DB.prepare(`UPDATE messages SET is_deleted = 1, content = NULL, media_url = NULL, link_url = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND group_id = ?`).bind(messageId, groupId).run();
    if (message.attachment_key && owner) await c.env.R2_BUCKET.delete(message.attachment_key).catch(() => undefined);
    return c.json({ success: true });
  } catch (e: any) { console.error('Message delete failed:', e); return error(c, 'Unable to delete message', 500); }
});

messageRouter.post('/:id/attachment', async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('id');
  const access = await groupAccess(c.env.DB, actor, groupId);
  if (!access.group) return error(c, 'Group not found', 404);
  if (!access.allowed) return error(c, 'You do not have access to this group', 403);
  const messageType = (c.req.header('X-Message-Type') || '').trim().toLowerCase();
  const mimeType = (c.req.header('Content-Type') || 'application/octet-stream').split(';')[0].trim().toLowerCase();
  const fileName = (c.req.header('X-File-Name') || 'attachment').trim().slice(0, 240);
  const declaredSize = Number(c.req.header('Content-Length') || 0);
  if (!['image', 'excel', 'pdf', 'audio'].includes(messageType)) return error(c, 'Unsupported attachment message type');
  if (!MESSAGE_TYPES.has(messageType)) return error(c, 'Invalid message type');
  if (!ALLOWED_MEDIA[messageType].has(mimeType)) return error(c, `Unsupported ${messageType} file type`);
  if (declaredSize > MAX_ATTACHMENT_BYTES) return error(c, 'Attachment must be 50 MB or smaller');
  if (!c.req.raw.body) return error(c, 'Attachment body is empty');
  const attachmentId = crypto.randomUUID();
  const safeName = fileName.replace(/[^a-zA-Z0-9._-]/g, '_') || 'attachment';
  const key = `groups/${groupId}/attachments/${attachmentId}-${safeName}`;
  await c.env.R2_BUCKET.put(key, c.req.raw.body, { httpMetadata: { contentType: mimeType, contentDisposition: `attachment; filename=\"${safeName}\"` }, customMetadata: { groupId, uploadedBy: actor.id, messageType } });
  const object = await c.env.R2_BUCKET.head(key);
  return c.json({ success: true, data: { attachment_key: key, file_name: safeName, mime_type: mimeType, file_size: object?.size || (declaredSize || null), message_type: messageType } }, 201);
});

messageRouter.get('/:id/attachment/:messageId', async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('id');
  const messageId = c.req.param('messageId');
  const access = await groupAccess(c.env.DB, actor, groupId);
  if (!access.group) return error(c, 'Group not found', 404);
  if (!access.allowed) return error(c, 'You do not have access to this group', 403);
  const message = await c.env.DB.prepare('SELECT attachment_key FROM messages WHERE id = ? AND group_id = ? AND is_deleted = 0 LIMIT 1').bind(messageId, groupId).first<any>();
  if (!message?.attachment_key) return error(c, 'Attachment not found', 404);
  const object = await c.env.R2_BUCKET.get(message.attachment_key);
  if (!object) return error(c, 'Attachment object not found', 404);
  const headers = new Headers(); object.writeHttpMetadata(headers); headers.set('Cache-Control', 'private, max-age=3600');
  return new Response(object.body, { status: 200, headers });
});

messageRouter.get('/:id/realtime', async (c) => {
  const actor = c.get('user');
  const groupId = c.req.param('id');
  if (c.req.header('Upgrade')?.toLowerCase() !== 'websocket') return error(c, 'WebSocket upgrade required', 426);
  const access = await groupAccess(c.env.DB, actor, groupId);
  if (!access.group) return error(c, 'Group not found', 404);
  if (!access.allowed) return error(c, 'You do not have access to this group', 403);
  const id = c.env.CHAT_ROOM.idFromName(groupId);
  const stub = c.env.CHAT_ROOM.get(id);
  const headers = new Headers(c.req.raw.headers);
  headers.set('X-Chat-User-Id', actor.id); headers.set('X-Chat-User-Name', actor.name); headers.set('X-Chat-User-Role', actor.role); headers.set('X-Chat-Group-Id', groupId);
  return stub.fetch(new Request(c.req.raw, { headers }));
});
