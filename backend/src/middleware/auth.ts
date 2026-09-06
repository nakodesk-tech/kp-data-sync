import { MiddlewareHandler } from 'hono';
import { Bindings, JWTPayload, UserRole, Variables } from '../types';
import { verifyJWT } from '../crypto';

export const authMiddleware = (allowedRoles?: UserRole[]): MiddlewareHandler<{ Bindings: Bindings; Variables: Variables }> => {
  return async (c, next) => {
    const authHeader = c.req.header('Authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return c.json(
        {
          success: false,
          error: 'Unauthorized: Missing or malformed Bearer token'
        },
        401
      );
    }

    const token = authHeader.substring(7).trim();
    const secret = c.env.JWT_SECRET || 'kp-data-sync-default-super-secret-key-change-in-prod';

    const payload = await verifyJWT<JWTPayload>(token, secret);
    if (!payload) {
      return c.json(
        {
          success: false,
          error: 'Unauthorized: Invalid or expired token'
        },
        401
      );
    }

    // Role-based Access Control Check
    if (allowedRoles && allowedRoles.length > 0) {
      if (!allowedRoles.includes(payload.role)) {
        return c.json(
          {
            success: false,
            error: `Forbidden: User role '${payload.role}' does not have permission for this resource.`
          },
          403
        );
      }
    }

    // Attach user to context
    c.set('user', payload);
    await next();
  };
};
