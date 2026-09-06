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
  mobile_number: string | null;
  password_hash: string;
  role: UserRole;
  cluster_name: string | null;
  cluster_code: string | null;
  school_name: string | null;
  school_code: string | null;
  address: string | null;
  status: 'Active' | 'Inactive';
  fcm_token: string | null;
  created_at: string;
  updated_at: string;
}

export interface JWTPayload {
  sub: string;
  id: string;
  name: string;
  email: string;
  role: UserRole;
  cluster_name: string | null;
  cluster_code: string | null;
  school_name: string | null;
  school_code: string | null;
  exp: number;
  iat: number;
}

export type Variables = {
  user: JWTPayload;
};
