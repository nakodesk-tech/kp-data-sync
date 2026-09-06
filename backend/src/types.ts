export type UserRole = 'Admin' | 'Cluster_Head' | 'School_HM' | 'Teacher';

export interface Bindings {
  DB: D1Database;
  R2_BUCKET: R2Bucket;
  JWT_SECRET?: string;
  SETUP_SECRET?: string;
  ENVIRONMENT?: string;
  CORS_ORIGIN?: string;
}

export interface UserRow {
  id: string;
  name: string;
  email: string;
  password_hash: string;
  role: UserRole;
  cluster_id: string | null;
  school_id: string | null;
  status: 'active' | 'inactive' | 'pending';
  created_at: string;
}

export interface JWTPayload {
  sub: string;
  id: string;
  name: string;
  email: string;
  role: UserRole;
  cluster_id: string | null;
  school_id: string | null;
  exp: number;
  iat: number;
}

export type Variables = {
  user: JWTPayload;
};
