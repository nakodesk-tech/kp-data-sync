import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { Bindings, Variables } from './types';
import { authRouter } from './routes/auth';
import { userRouter } from './routes/user';
import { schoolRouter } from './routes/schools';
import { groupRouter } from './routes/groups';

const app = new Hono<{ Bindings: Bindings; Variables: Variables }>();

const BACKEND_BUILD = 'group-creation-v1';

app.use('*', async (c, next) => {
  const allowedOrigin = c.env.CORS_ORIGIN || '*';
  const corsMiddleware = cors({ origin: allowedOrigin, allowMethods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'], allowHeaders: ['Content-Type', 'Authorization', 'X-Setup-Secret'], exposeHeaders: ['Content-Length'], maxAge: 86400 });
  return corsMiddleware(c, next);
});

app.get('/', (c) => c.json({
  status: 'online', app: 'KP Data Sync Backend', version: '1.0.0', build: BACKEND_BUILD,
  framework: 'Hono.js + Cloudflare Workers',
  storage: { d1: 'Cloudflare D1 (SQLite)', r2: 'Cloudflare R2 Object Storage (R2_BUCKET)' },
  endpoints: {
    health: 'GET /', login: 'POST /api/auth/login', setupAdmin: 'POST /api/auth/setup-admin',
    schools: 'GET /api/schools', registerSchool: 'POST /api/schools/register',
    updateSchool: 'PATCH /api/schools/:id', deleteSchool: 'DELETE /api/schools/:id',
    users: 'GET /api/user/directory', registerUser: 'POST /api/user/register', adminOverview: 'GET /api/user/admin/overview',
    groups: 'GET /api/groups', createGroup: 'POST /api/groups', groupPhoto: 'GET /api/groups/:id/photo'
  }
}));

app.route('/api/auth', authRouter);
app.route('/api/user', userRouter);
app.route('/api/schools', schoolRouter);
app.route('/api/groups', groupRouter);

app.notFound((c) => c.json({ success: false, error: 'Endpoint not found' }, 404));
app.onError((err, c) => c.json({ success: false, error: err.message || 'Internal Server Error' }, 500));

export default app;
