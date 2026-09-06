import { Hono } from 'hono';
import { Bindings, UserRow, Variables } from '../types';
import { hashPassword, verifyPassword, signJWT } from '../crypto';

export const authRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

// Constant-time string comparison utility for secrets
function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

// Simple email format validation regex
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// ============================================================================
// POST /api/auth/login
// User authentication with email/password, RBAC role validation, and JWT issuing
// ============================================================================
authRouter.post('/login', async (c) => {
  try {
    const body = await c.req.json().catch(() => ({}));
    const { email, password, role: requestedRole } = body;

    // 1. Missing credentials validation (must return 400 Bad Request)
    if (!email || typeof email !== 'string' || !password || typeof password !== 'string') {
      return c.json(
        {
          success: false,
          error: 'Email and password are required'
        },
        400
      );
    }

    const normalizedEmail = email.toLowerCase().trim();

    if (!normalizedEmail || !EMAIL_REGEX.test(normalizedEmail)) {
      return c.json(
        {
          success: false,
          error: 'Please provide a valid email address'
        },
        400
      );
    }

    // 2. Query user by normalized email from Cloudflare D1
    const user = await c.env.DB
      .prepare('SELECT * FROM users WHERE email = ?')
      .bind(normalizedEmail)
      .first<UserRow>();

    if (!user) {
      return c.json(
        {
          success: false,
          error: 'Invalid email or password'
        },
        401
      );
    }

    // 3. Account status validation
    if (user.status === 'inactive') {
      return c.json(
        {
          success: false,
          error: 'User account is inactive. Please contact the App Admin.'
        },
        403
      );
    }

    if (user.status === 'pending') {
      return c.json(
        {
          success: false,
          error: 'User account approval is pending. Please contact the App Admin.'
        },
        403
      );
    }

    if (user.status !== 'active') {
      return c.json(
        {
          success: false,
          error: `User account is currently ${user.status}. Access denied.`
        },
        403
      );
    }

    // 4. Role validation if client explicitly requested a role
    if (requestedRole && user.role !== requestedRole) {
      return c.json(
        {
          success: false,
          error: `Account role '${user.role}' does not match requested role '${requestedRole}'`
        },
        403
      );
    }

    // 5. Verify Password Hash via PBKDF2 Web Crypto
    const isValid = await verifyPassword(password, user.password_hash);
    if (!isValid) {
      return c.json(
        {
          success: false,
          error: 'Invalid email or password'
        },
        401
      );
    }

    // 6. Require JWT_SECRET from Cloudflare Worker Secret environment
    const secret = c.env.JWT_SECRET;
    if (!secret) {
      console.error('CRITICAL: JWT_SECRET is not configured on this Worker environment.');
      return c.json(
        {
          success: false,
          error: 'Server configuration error: JWT_SECRET is not configured'
        },
        500
      );
    }

    // 7. Generate JWT (valid for 7 days)
    const now = Math.floor(Date.now() / 1000);
    const exp = now + 7 * 24 * 60 * 60; // 7 days

    const tokenPayload = {
      sub: user.id,
      id: user.id,
      name: user.name,
      email: user.email,
      role: user.role,
      cluster_id: user.cluster_id,
      school_id: user.school_id,
      iat: now,
      exp: exp
    };

    const token = await signJWT(tokenPayload, secret);

    return c.json({
      success: true,
      message: 'Login successful',
      token,
      user: {
        id: user.id,
        name: user.name,
        email: user.email,
        role: user.role,
        cluster_id: user.cluster_id,
        school_id: user.school_id,
        status: user.status
      }
    });
  } catch (error: any) {
    return c.json(
      {
        success: false,
        error: error.message || 'Internal server error during authentication'
      },
      500
    );
  }
});

// ============================================================================
// POST /api/auth/setup-admin
// Secure initial Admin bootstrap mechanism.
// Only accessible when no Admin user exists in D1 and protected by SETUP_SECRET.
// ============================================================================
authRouter.post('/setup-admin', async (c) => {
  try {
    // 1. Verify that SETUP_SECRET is configured on the server
    const serverSetupSecret = c.env.SETUP_SECRET;
    if (!serverSetupSecret) {
      console.error('CRITICAL: SETUP_SECRET is not configured on this Worker.');
      return c.json(
        {
          success: false,
          error: 'Server configuration error: SETUP_SECRET is not configured on this server.'
        },
        500
      );
    }

    // 2. Extract client setup secret from header or request body
    const authHeader = c.req.header('Authorization');
    const customHeader = c.req.header('X-Setup-Secret');
    let providedSecret = '';

    if (customHeader) {
      providedSecret = customHeader.trim();
    } else if (authHeader && authHeader.startsWith('Bearer ')) {
      providedSecret = authHeader.substring(7).trim();
    }

    const body = await c.req.json().catch(() => ({}));
    if (!providedSecret && body.setup_secret && typeof body.setup_secret === 'string') {
      providedSecret = body.setup_secret.trim();
    }

    if (!providedSecret || !constantTimeEqual(providedSecret, serverSetupSecret)) {
      return c.json(
        {
          success: false,
          error: 'Unauthorized: Invalid or missing setup secret'
        },
        401
      );
    }

    // 3. Verify no Admin exists in Cloudflare D1
    const adminCountRow = await c.env.DB
      .prepare("SELECT COUNT(*) as count FROM users WHERE role = 'Admin'")
      .first<{ count: number }>();

    const adminCount = adminCountRow?.count || 0;
    if (adminCount > 0) {
      return c.json(
        {
          success: false,
          error: 'Setup is disabled: An Admin user already exists in the system.'
        },
        409
      );
    }

    // 4. Validate input fields
    const { name, email, password } = body;

    if (!name || typeof name !== 'string' || name.trim().length < 2) {
      return c.json(
        {
          success: false,
          error: 'Valid Admin name is required (minimum 2 characters)'
        },
        400
      );
    }

    if (!email || typeof email !== 'string') {
      return c.json(
        {
          success: false,
          error: 'Valid Admin email address is required'
        },
        400
      );
    }

    const normalizedEmail = email.toLowerCase().trim();
    if (!EMAIL_REGEX.test(normalizedEmail)) {
      return c.json(
        {
          success: false,
          error: 'Please provide a valid email address'
        },
        400
      );
    }

    if (!password || typeof password !== 'string' || password.length < 8) {
      return c.json(
        {
          success: false,
          error: 'Password must be at least 8 characters long'
        },
        400
      );
    }

    // 5. Check if user with this email already exists
    const existingUser = await c.env.DB
      .prepare('SELECT id FROM users WHERE email = ?')
      .bind(normalizedEmail)
      .first<{ id: string }>();

    if (existingUser) {
      return c.json(
        {
          success: false,
          error: 'A user with this email address already exists.'
        },
        409
      );
    }

    // 6. Hash password with PBKDF2-HMAC-SHA256 (100,000 iterations)
    const passwordHash = await hashPassword(password);
    const adminId = crypto.randomUUID();

    // 7. Insert the initial Admin into D1
    await c.env.DB
      .prepare(
        "INSERT INTO users (id, name, email, password_hash, role, cluster_id, school_id, status) VALUES (?, ?, ?, ?, 'Admin', NULL, NULL, 'active')"
      )
      .bind(adminId, name.trim(), normalizedEmail, passwordHash)
      .run();

    return c.json(
      {
        success: true,
        message: 'Initial Admin user created successfully',
        user: {
          id: adminId,
          name: name.trim(),
          email: normalizedEmail,
          role: 'Admin',
          status: 'active'
        }
      },
      201
    );
  } catch (error: any) {
    return c.json(
      {
        success: false,
        error: error.message || 'Internal server error during admin bootstrap'
      },
      500
    );
  }
});
