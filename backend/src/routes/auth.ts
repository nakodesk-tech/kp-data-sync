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

authRouter.post('/login', async (c) => {
  try {
    const body = await c.req.json().catch(() => ({}));
    const { email, password, role: requestedRole } = body;
    if (!email || typeof email !== 'string' || !password || typeof password !== 'string') return c.json({ success: false, error: 'Email and password are required' }, 400);
    const normalizedEmail = email.toLowerCase().trim();
    if (!EMAIL_REGEX.test(normalizedEmail)) return c.json({ success: false, error: 'Please provide a valid email address' }, 400);
    const user = await c.env.DB.prepare('SELECT * FROM users WHERE email = ?').bind(normalizedEmail).first<UserRow>();
    if (!user) return c.json({ success: false, error: 'Invalid email or password' }, 401);
    if (user.status !== 'active') return c.json({ success: false, error: user.status === 'pending' ? 'User account approval is pending. Please contact the App Admin.' : 'User account is inactive. Please contact the App Admin.' }, 403);
    if (requestedRole && user.role !== requestedRole) return c.json({ success: false, error: `Account role '${user.role}' does not match requested role '${requestedRole}'` }, 403);
    const isValid = await verifyPassword(password, user.password_hash);
    if (!isValid) return c.json({ success: false, error: 'Invalid email or password' }, 401);
    const secret = c.env.JWT_SECRET;
    if (!secret) return c.json({ success: false, error: 'Server configuration error: JWT_SECRET is not configured' }, 500);
    const now = Math.floor(Date.now() / 1000);
    const token = await signJWT({ sub: user.id, id: user.id, name: user.name, email: user.email, role: user.role, cluster_name: user.cluster_name, cluster_code: user.cluster_code, school_name: user.school_name, school_code: user.school_code, iat: now, exp: now + 7 * 24 * 60 * 60 }, secret);
    return c.json({ success: true, message: 'Login successful', token, user: { id: user.id, name: user.name, email: user.email, role: user.role, cluster_name: user.cluster_name, cluster_code: user.cluster_code, school_name: user.school_name, school_code: user.school_code, status: user.status } });
  } catch (error: any) {
    console.error('LOGIN_ERROR:', error);
    return c.json({ success: false, error: error.message || 'Internal server error during authentication' }, 500);
  }
});

authRouter.post('/setup-admin', async (c) => {
  let stage = 'start';
  try {
    stage = 'secret configuration';
    const serverSetupSecret = c.env.SETUP_SECRET;
    if (!serverSetupSecret) return c.json({ success: false, error: 'Server configuration error: SETUP_SECRET is not configured on this server.' }, 500);
    stage = 'database binding';
    if (!c.env.DB) return c.json({ success: false, error: 'Server configuration error: D1 database binding DB is not configured.' }, 500);
    stage = 'users table preflight';
    try {
      await c.env.DB.prepare('SELECT 1 FROM users LIMIT 1').first();
    } catch (error: any) {
      return c.json({ success: false, error: `Database error during users table check: ${error?.message || 'unknown database error'}` }, 500);
    }
    stage = 'setup secret validation';
    const authHeader = c.req.header('Authorization');
    const customHeader = c.req.header('X-Setup-Secret');
    let providedSecret = customHeader?.trim() || (authHeader?.startsWith('Bearer ') ? authHeader.substring(7).trim() : '');
    const body = await c.req.json().catch(() => ({}));
    if (!providedSecret && typeof body.setup_secret === 'string') providedSecret = body.setup_secret.trim();
    if (!providedSecret || !constantTimeEqual(providedSecret, serverSetupSecret)) return c.json({ success: false, error: 'Unauthorized: Invalid or missing setup secret' }, 401);
    stage = 'admin count query';
    let adminCountRow;
    try {
      adminCountRow = await c.env.DB.prepare("SELECT COUNT(*) as count FROM users WHERE role = 'Admin'").first<{ count: number }>();
    } catch (error: any) {
      return c.json({ success: false, error: `Database error during Admin check: ${error?.message || 'unknown database error'}` }, 500);
    }
    if ((adminCountRow?.count || 0) > 0) return c.json({ success: false, error: 'Setup is disabled: An Admin user already exists in the system.' }, 409);
    stage = 'request validation';
    const { name, email, password } = body;
    if (!name || typeof name !== 'string' || name.trim().length < 2) return c.json({ success: false, error: 'Valid Admin name is required (minimum 2 characters)' }, 400);
    if (!email || typeof email !== 'string') return c.json({ success: false, error: 'Valid Admin email address is required' }, 400);
    const normalizedEmail = email.toLowerCase().trim();
    if (!EMAIL_REGEX.test(normalizedEmail)) return c.json({ success: false, error: 'Please provide a valid email address' }, 400);
    if (!password || typeof password !== 'string' || password.length < 8) return c.json({ success: false, error: 'Password must be at least 8 characters long' }, 400);
    stage = 'email duplicate check';
    try {
      const existingUser = await c.env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(normalizedEmail).first<{ id: string }>();
      if (existingUser) return c.json({ success: false, error: 'A user with this email address already exists.' }, 409);
    } catch (error: any) {
      return c.json({ success: false, error: `Database error during email check: ${error?.message || 'unknown database error'}` }, 500);
    }
    stage = 'password hashing';
    let passwordHash: string;
    try {
      passwordHash = await hashPassword(password);
    } catch (error: any) {
      return c.json({ success: false, error: `Password hashing failed: ${error?.message || 'unknown error'}` }, 500);
    }
    stage = 'admin insert';
    const adminId = crypto.randomUUID();
    try {
      // Production D1 currently has mobile_number as NOT NULL. Admin registration does not collect a mobile number, so store an empty value for compatibility.
      await c.env.DB.prepare(`INSERT INTO users (id, name, email, mobile_number, password_hash, role, status) VALUES (?, ?, ?, ?, ?, 'Admin', 'active')`).bind(adminId, name.trim(), normalizedEmail, typeof body.mobile_number === 'string' ? body.mobile_number.trim() : '', passwordHash).run();
    } catch (error: any) {
      console.error('SETUP_ADMIN_ERROR [admin insert]:', error);
      return c.json({ success: false, error: `Database error during Admin creation: ${error?.message || 'unknown database error'}` }, 500);
    }
    return c.json({ success: true, message: 'Initial Admin user created successfully', user: { id: adminId, name: name.trim(), email: normalizedEmail, role: 'Admin', status: 'active' } }, 201);
  } catch (error: any) {
    console.error(`SETUP_ADMIN_ERROR [${stage}]:`, error);
    return c.json({ success: false, error: `Admin bootstrap failed at ${stage}: ${error?.message || 'Internal server error'}` }, 500);
  }
});
