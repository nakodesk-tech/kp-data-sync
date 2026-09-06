import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { Bindings, Variables } from './types';
import { authRouter } from './routes/auth';
import { userRouter } from './routes/user';

const app = new Hono<{ Bindings: Bindings; Variables: Variables }>();

// Enable CORS for Android client & web administration
app.use('*', async (c, next) => {
  const allowedOrigin = c.env.CORS_ORIGIN || '*';
  const corsMiddleware = cors({
    origin: allowedOrigin,
    allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowHeaders: ['Content-Type', 'Authorization', 'X-Setup-Secret'],
    exposeHeaders: ['Content-Length'],
    maxAge: 86400,
  });
  return corsMiddleware(c, next);
});

// Root / Health Check
app.get('/', (c) => {
  return c.json({
    status: 'online',
    app: 'KP Data Sync Backend',
    version: '1.0.0',
    framework: 'Hono.js + Cloudflare Workers',
    storage: {
      d1: 'Cloudflare D1 (SQLite)',
      r2: 'Cloudflare R2 Object Storage (REPORTS_BUCKET)'
    },
    endpoints: {
      health: 'GET /',
      login: 'POST /api/auth/login',
      setupAdmin: 'POST /api/auth/setup-admin',
      profile: 'GET /api/user/profile',
      adminOverview: 'GET /api/user/admin/overview'
    }
  });
});

// API Routes
app.route('/api/auth', authRouter);
app.route('/api/user', userRouter);

// 404 Handler
app.notFound((c) => {
  return c.json({ success: false, error: 'Endpoint not found' }, 404);
});

// Global Error Handler
app.onError((err, c) => {
  console.error('Worker Error:', err);
  return c.json(
    {
      success: false,
      error: err.message || 'Internal Server Error'
    },
    500
  );
});

export default app;
