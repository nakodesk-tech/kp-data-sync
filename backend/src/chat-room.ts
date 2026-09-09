import { Bindings } from './types';

const MESSAGE_TYPES = new Set(['text', 'image', 'excel', 'pdf', 'audio', 'link']);

interface ChatSession { userId: string; name: string; role: string; }
interface IncomingMessage { type: 'message'; client_message_id?: string; message_type: string; content?: string | null; attachment_key?: string | null; file_name?: string | null; mime_type?: string | null; file_size?: number | null; link_url?: string | null; }

export class ChatRoom {
  constructor(private readonly state: DurableObjectState, private readonly env: Bindings) {}

  async fetch(request: Request): Promise<Response> {
    if (request.headers.get('Upgrade')?.toLowerCase() !== 'websocket') return new Response('Expected WebSocket', { status: 426 });
    const userId = request.headers.get('X-Chat-User-Id');
    const name = request.headers.get('X-Chat-User-Name');
    const role = request.headers.get('X-Chat-User-Role');
    const groupId = request.headers.get('X-Chat-Group-Id');
    if (!userId || !name || !role || !groupId) return new Response('Unauthorized', { status: 401 });
    const member = await this.env.DB.prepare('SELECT id FROM group_members WHERE group_id = ? AND user_id = ? AND is_active = 1 LIMIT 1').bind(groupId, userId).first();
    const group = await this.env.DB.prepare('SELECT id, is_active FROM groups WHERE id = ? LIMIT 1').bind(groupId).first<any>();
    if (!group || group.is_active !== 1 || !member) return new Response('Forbidden', { status: 403 });
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.state.acceptWebSocket(server);
    server.serializeAttachment({ userId, name, role, groupId } satisfies ChatSession & { groupId: string });
    server.send(JSON.stringify({ type: 'connected', group_id: groupId }));
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws: WebSocket, raw: string | ArrayBuffer) {
    const session = ws.deserializeAttachment() as (ChatSession & { groupId: string }) | null;
    if (!session) return ws.close(1008, 'Session expired');
    const member = await this.env.DB.prepare('SELECT id FROM group_members WHERE group_id = ? AND user_id = ? AND is_active = 1 LIMIT 1').bind(session.groupId, session.userId).first();
    if (!member) return ws.close(1008, 'No longer a group member');
    if (typeof raw !== 'string') return ws.send(JSON.stringify({ type: 'error', error: 'Binary WebSocket messages are not supported; upload media first.' }));
    let input: any;
    try { input = JSON.parse(raw); } catch { return ws.send(JSON.stringify({ type: 'error', error: 'Invalid message JSON' })); }

    if (input.type === 'delete_message') {
      const message = await this.env.DB.prepare('SELECT id, sender_id FROM messages WHERE id = ? AND group_id = ? LIMIT 1').bind(String(input.message_id || ''), session.groupId).first<any>();
      if (!message) return ws.send(JSON.stringify({ type: 'error', error: 'Message not found.' }));
      const group = await this.env.DB.prepare('SELECT created_by, is_active FROM groups WHERE id = ? LIMIT 1').bind(session.groupId).first<any>();
      const allowed = message.sender_id === session.userId || group?.created_by === session.userId || session.role === 'Admin';
      if (!allowed) return ws.send(JSON.stringify({ type: 'error', error: 'Only the sender or group owner can delete this message.' }));
      await this.env.DB.prepare('UPDATE messages SET is_deleted = 1, content = NULL, attachment_key = NULL, file_name = NULL, media_url = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND group_id = ?').bind(message.id, session.groupId).run();
      const payload = JSON.stringify({ type: 'message_deleted', message_id: message.id, deleted_by: session.userId });
      for (const socket of this.state.getWebSockets()) { try { socket.send(payload); } catch {} }
      return;
    }

    if (input.type === 'group_action') {
      const group = await this.env.DB.prepare('SELECT created_by, is_active FROM groups WHERE id = ? LIMIT 1').bind(session.groupId).first<any>();
      if (!group || group.is_active !== 1) return ws.send(JSON.stringify({ type: 'error', error: 'Group is no longer active.' }));
      if (group.created_by !== session.userId && session.role !== 'Admin') return ws.send(JSON.stringify({ type: 'error', error: 'Only the group owner can manage this group.' }));
      if (input.action === 'close') {
        await this.env.DB.prepare('UPDATE groups SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?').bind(session.groupId).run();
        const payload = JSON.stringify({ type: 'group_deleted', group_id: session.groupId });
        for (const socket of this.state.getWebSockets()) { try { socket.send(payload); } catch {} }
        for (const socket of this.state.getWebSockets()) { try { socket.close(1000, 'Group closed by owner'); } catch {} }
        return;
      }
      return ws.send(JSON.stringify({ type: 'error', error: 'Unknown group action.' }));
    }

    if (input.type !== 'message' || !MESSAGE_TYPES.has(input.message_type)) return ws.send(JSON.stringify({ type: 'error', error: 'Invalid message type' }));
    const messageType = input.message_type;
    const content = typeof input.content === 'string' ? input.content.trim() : null;
    const linkUrl = typeof input.link_url === 'string' ? input.link_url.trim() : null;
    if (messageType === 'text' && !content) return ws.send(JSON.stringify({ type: 'error', error: 'Text message cannot be empty' }));
    if (messageType === 'link' && (!linkUrl || !/^https?:\/\//i.test(linkUrl))) return ws.send(JSON.stringify({ type: 'error', error: 'Link must be a valid http or https URL' }));
    if (['image', 'excel', 'pdf', 'audio'].includes(messageType) && !input.attachment_key) return ws.send(JSON.stringify({ type: 'error', error: 'Attachment is required for this message type' }));
    if (input.attachment_key) {
      const object = await this.env.R2_BUCKET.head(input.attachment_key);
      if (!object || !input.attachment_key.startsWith(`groups/${session.groupId}/attachments/`)) return ws.send(JSON.stringify({ type: 'error', error: 'Attachment not found or invalid for this group' }));
    }
    const clientMessageId = typeof input.client_message_id === 'string' ? input.client_message_id.trim().slice(0, 100) : null;
    const columns = 'id, group_id, group_name, sender_id, sender_name, content, media_url, message_type, attachment_key, file_name, mime_type, file_size, link_url, client_message_id, is_deleted, is_read, created_at, updated_at, excel_status, excel_version, excel_published_at, excel_published_by';
    if (clientMessageId) {
      const existing = await this.env.DB.prepare(`SELECT ${columns} FROM messages WHERE group_id = ? AND sender_id = ? AND client_message_id = ? LIMIT 1`).bind(session.groupId, session.userId, clientMessageId).first<any>();
      if (existing) return ws.send(JSON.stringify({ type: 'message', data: existing, duplicate: true }));
    }
    const group = await this.env.DB.prepare('SELECT group_name FROM groups WHERE id = ? LIMIT 1').bind(session.groupId).first<any>();
    const messageId = `msg-${crypto.randomUUID()}`;
    await this.env.DB.prepare('INSERT INTO messages (id, group_id, group_name, sender_id, sender_name, content, media_url, message_type, attachment_key, file_name, mime_type, file_size, link_url, client_message_id, is_deleted, is_read) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)').bind(messageId, session.groupId, group?.group_name || null, session.userId, session.name, content, input.attachment_key || null, messageType, input.attachment_key || null, input.file_name || null, input.mime_type || null, typeof input.file_size === 'number' ? input.file_size : null, linkUrl, clientMessageId).run();
    const saved = await this.env.DB.prepare(`SELECT ${columns} FROM messages WHERE id = ?`).bind(messageId).first<any>();
    const payload = JSON.stringify({ type: 'message', data: saved });
    for (const socket of this.state.getWebSockets()) { try { socket.send(payload); } catch {} }
  }

  async webSocketClose(ws: WebSocket) { try { ws.close(); } catch {} }
  async webSocketError(ws: WebSocket) { try { ws.close(1011, 'WebSocket error'); } catch {} }
}
