import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { Bindings, Variables } from './types';
import { authRouter } from './routes/auth';
import { userRouter } from './routes/user';
import { schoolRouter } from './routes/schools';
import { groupRouter } from './routes/groups';
import { groupManagementRouter } from './routes/group-management';
import { messageRouter } from './routes/messages';
import { excelRouter } from './routes/excel';
import { notificationRouter } from './routes/notifications';
import { ChatRoom } from './chat-room';

const app = new Hono<{ Bindings: Bindings; Variables: Variables }>();
const BACKEND_BUILD = 'excel-collaboration-v1';

app.use('*', async (c, next) => {
  const allowedOrigin = c.env.CORS_ORIGIN || '*';
  const corsMiddleware = cors({ origin: allowedOrigin, allowMethods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'], allowHeaders: ['Content-Type', 'Authorization', 'X-Setup-Secret', 'X-Message-Type', 'X-File-Name'], exposeHeaders: ['Content-Length'], maxAge: 86400 });
  return corsMiddleware(c, next);
});

app.get('/', (c) => c.json({
  status: 'online', app: 'KP Data Sync Backend', version: '1.0.0', build: BACKEND_BUILD,
  framework: 'Hono.js + Cloudflare Workers',
  storage: { d1: 'Cloudflare D1 (SQLite)', r2: 'Cloudflare R2 Object Storage (R2_BUCKET)', realtime: 'Cloudflare Durable Objects + WebSocket Hibernation' },
  endpoints: {
    health: 'GET /', login: 'POST /api/auth/login', setupAdmin: 'POST /api/auth/setup-admin',
    schools: 'GET /api/schools', registerSchool: 'POST /api/schools/register', updateSchool: 'PATCH /api/schools/:id', deleteSchool: 'DELETE /api/schools/:id',
    users: 'GET /api/user/directory', registerUser: 'POST /api/user/register', adminOverview: 'GET /api/user/admin/overview',
    groups: 'GET /api/groups', createGroup: 'POST /api/groups', groupInfo: 'GET /api/groups/:id', updateGroup: 'PATCH /api/groups/:id', closeGroup: 'DELETE /api/groups/:id', groupMembers: 'POST/DELETE /api/groups/:id/members', groupMemberManagement: 'GET/POST/PATCH/DELETE /api/groups/:id/management/members', groupPhoto: 'GET /api/groups/:id/photo',
    messages: 'GET /api/messages/:groupId', deleteMessage: 'DELETE /api/messages/:groupId/:messageId', messageAttachment: 'POST /api/messages/:groupId/attachment', messageAttachmentRead: 'GET /api/messages/:groupId/attachment/:messageId', realtime: 'GET /api/messages/:groupId/realtime',
    excel: 'PUT /api/excel/:groupId/:messageId, POST /api/excel/:groupId/:messageId/publish, GET /api/excel/reports, GET /api/excel/:groupId/:messageId/status',
    notifications: 'POST /api/notifications, POST /api/notifications/:id/publish, GET /api/notifications, GET /api/notifications/unread-count, POST /api/notifications/:id/read, POST /api/notifications/:id/dismiss'
  }
}));

app.route('/api/auth', authRouter);
app.route('/api/user', userRouter);
app.route('/api/schools', schoolRouter);
app.route('/api/groups', groupManagementRouter);
app.route('/api/groups', groupRouter);
app.route('/api/messages', messageRouter);
app.route('/api/excel', excelRouter);
app.route('/api/notifications', notificationRouter);

app.notFound((c) => c.json({ success: false, error: 'Endpoint not found' }, 404));
app.onError((err, c) => c.json({ success: false, error: err.message || 'Internal Server Error' }, 500));

export { ChatRoom };
export default app;
