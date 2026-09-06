import { Hono } from 'hono';
import { Bindings, UserRow, Variables } from '../types';
import { verifyPassword, signJWT } from '../crypto';

export const authRouter = new Hono<{ Bindings: Bindings; Variables: Variables }>();

authRouter.post('/login', async (c) => {
  try {
    const body = await c.req.json();
    const { email, password, role: requestedRole } = body;

    if (!email || !password) {
      return c.json(
        {
          success: false,
          error: 'Email and password are required'
        },
        404
      );
    }

    // Query user by email from Cloudflare D1
    const user = await c.env.DB
      .prepare('SELECT * FROM users WHERE email = ?')
      .bind(email.toLowerCase().trim())
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

    if (user.status !== 'active') {
      return c.json(
        {
          success: false,
          error: `User account is currently ${user.status}. Please contact App Admin.`
        },
        403
      );
    }

    // If a role was specifically selected in the UI, verify role match
    if (requestedRole && user.role !== requestedRole) {
      return c.json(
        {
          success: false,
          error: `Account role '${user.role}' does not match requested role '${requestedRole}'`
        },
        403
      );
    }

    // Verify Password Hash via PBKDF2 Web Crypto
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

    // Generate JWT (valid for 7 days)
    const secret = c.env.JWT_SECRET || 'kp-data-sync-default-super-secret-key-change-in-prod';
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
