import { Hono } from 'hono';
import { Bindings, UserRow, Variables } from '../types';
import { hashPassword, verifyPassword, signJWT } from '../crypto';

export const authRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// POST /api/auth/login
authRouter.post('/login', async (c) => {
  try {
    const body = await c.req.json().catch(() => ({}));
    const { email, password, role: requestedRole } = body;

    if (!email || typeof email !== 'string' || !password || typeof password !== 'string') {
      return c.json({ success: false, error: 'Email and password are required' }, 400);
    }

    const normalizedEmail = email.toLowerCase().trim();
    if (!EMAIL_REGEX.test(normalizedEmail)) {
      return c.json({ success: false, error: 'Please provide a valid email address' }, 400);
    }

    const user = await c.env.DB
      .prepare('SELECT * FROM users WHERE email = ?')
      .bind(normalizedEmail)
      .first<UserRow>();

    if (!user) return c.json({ success: false, error: 'Invalid email or password' }, 401);

    if (user.status !== 'active') {
      return c.json({
        success: false,
        error: user.status === 'pending'
          ? 'User account approval is pending. Please contact the App Admin.'
          : 'User account is inactive. Please contact the App Admin.'
      }, 403);
    }

    if (requestedRole && user.role !== requestedRole) {
      return c.json({
        success: false,
        error: `Account role '${user.role}' does not match requested role '${requestedRole}'`
      }, 403);
    }

    const isValid = await verifyPassword(password, user.password_hash);
    if (!isValid) return c.json({ success: false, error: 'Invalid email or password' }, 401);

    const secret = c.env.JWT_SECRET;
    if (!secret) {
      console.error('CRITICAL: JWT_SECRET is not configured on this Worker environment.');
      return c.json({ success: false, error: 'Server configuration error: JWT_SECRET is not configured' }, 500);
    }

    const now = Math.floor(Date.now() / 1000);
    const exp = now + 7 * 24 * 60 * 60;
    const token = await signJWT({
      sub: user.id,
      id: user.id,
      name: user.name,
      email: user.email,
      role: user.role,
      cluster_name: user.cluster_name,
      cluster_code: user.cluster_code,
      school_name: user.school_name,
      school_code: user.school_code,
      iat: now,
      exp
    }, secret);

    return c.json({
      success: true,
      message: 'Login successful',
      token,
      user: {
        id: user.id,
        name: user.name,
        email: user.email,
        role: user.role,
        cluster_name: user.cluster_name,
        cluster_code: user.cluster_code,
        school_name: user.school_name,
        school_code: user.school_code,
        status: user.status
      }
    });
  } catch (error: any) {
    return c.json({ success: false, error: error.message || 'Internal server error during authentication' }, 500);
  }
});

// POST /api/auth/setup-admin
// Creates the first Admin only; protected by SETUP_SECRET.
authRouter.post('/setup-admin', async (c) => {
  try {
    const serverSetupSecret = c.env.SETUP_SECRET;
    if (!serverSetupSecret) {
      console.error('CRITICAL: SETUP_SECRET is not configured on this Worker.');
      return c.json({ success: false, error: 'Server configuration error: SETUP_SECRET is not configured on this server.' }, 500);
    }

    if (!c.env.DB) {
      console.error('CRITICAL: D1 binding DB is not configured on this Worker.');
      return c.json({ success: false, error: 'Server configuration error: D1 database binding DB is not configured.' }, 500);
    }

    // Fail early with a clear deployment/schema error instead of a generic
    // database exception if the production D1 migration has not been applied.
    try {
      await c.env.DB.prepare('SELECT 1 FROM users LIMIT 1').first();
    } catch (error: any) {
      console.error('CRITICAL: D1 users table is unavailable:', error);
      return c.json({
        success: false,
        error: 'Server database error: the users table is not available. Please apply the production D1 migrations before creating the first Admin.'
      }, 500);
    }

    const authHeader = c.req.header('Authorization');
    const customHeader = c.req.header('X-Setup-Secret');
    let providedSecret = '';
    if (customHeader) providedSecret = customHeader.trim();
    else if (authHeader?.startsWith('Bearer ')) providedSecret = authHeader.substring(7).trim();

    const body = await c.req.json().catch(() => ({}));
    if (!providedSecret && typeof body.setup_secret === 'string') providedSecret = body.setup_secret.trim();
    if (!providedSecret || !constantTimeEqual(providedSecret, serverSetupSecret)) {
      return c.json({ success: false, error: 'Unauthorized: Invalid or missing setup secret' }, 401);
    }

    const adminCountRow = await c.env.DB
      .prepare("SELECT COUNT(*) as count FROM users WHERE role = 'Admin'")
      .first<{ count: number }>();
    if ((adminCountRow?.count || 0) > 0) {
      return c.json({ success: false, error: 'Setup is disabled: An Admin user already exists in the system.' }, 409);
    }

    const { name, email, password } = body;
    if (!name || typeof name !== 'string' || name.trim().length < 2) {
      return c.json({ success: false, error: 'Valid Admin name is required (minimum 2 characters)' }, 400);
    }
    if (!email || typeof email !== 'string') {
      return c.json({ success: false, error: 'Valid Admin email address is required' }, 400);
    }
    const normalizedEmail = email.toLowerCase().trim();
    if (!EMAIL_REGEX.test(normalizedEmail)) {
      return c.json({ success: false, error: 'Please provide a valid email address' }, 400);
    }
    if (!password || typeof password !== 'string' || password.length < 8) {
      return c.json({ success: false, error: 'Password must be at least 8 characters long' }, 400);
    }

    const existingUser = await c.env.DB
      .prepare('SELECT id FROM users WHERE email = ?')
      .bind(normalizedEmail)
      .first<{ id: string }>();
    if (existingUser) return c.json({ success: false, error: 'A user with this email address already exists.' }, 409);

    const passwordHash = await hashPassword(password);
    const adminId = crypto.randomUUID();
    await c.env.DB
      .prepare("INSERT INTO users (id, name, email, password_hash, role, status) VALUES (?, ?, ?, ?, 'Admin', 'active')")
      .bind(adminId, name.trim(), normalizedEmail, passwordHash)
      .run();

    return c.json({
      success: true,
      message: 'Initial Admin user created successfully',
      user: { id: adminId, name: name.trim(), email: normalizedEmail, role: 'Admin', status: 'active' }
    }, 201);
  } catch (error: any) {
    console.error('Initial Admin bootstrap failed:', error);
    return c.json({ success: false, error: error.message || 'Internal server error during admin bootstrap' }, 500);
  }
});
