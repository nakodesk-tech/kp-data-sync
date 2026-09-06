# KP Data Sync - Cloudflare Backend Architecture & Deployment Guide

Production-ready backend API for **KP Data Sync (School Data Management & Chat App)**.

## Architectural Stack
- **Compute:** Cloudflare Workers running [Hono.js](https://hono.dev/) framework (multi-file modular architecture).
- **Database:** Cloudflare D1 (SQLite) with migrations support (`DB` binding).
- **Object Storage:** Cloudflare R2 (`REPORTS_BUCKET` binding) for Excel spreadsheets, reports, and media attachments.
- **Cryptography:** Native Web Crypto API using PBKDF2-HMAC-SHA256 (100,000 iterations) for password hashing and HS256 for JWT token signing/verification. Zero third-party crypto dependencies.
- **Security & RBAC:** Role-Based Access Control enforcing permissions across 4 roles: `Admin`, `Cluster_Head`, `School_HM`, and `Teacher`.
- **First-Admin Bootstrap:** Secure, secret-gated endpoint (`POST /api/auth/setup-admin`) that operates strictly when no Admin account exists.

---

## 1. Directory Structure

```
backend/
├── schema.sql                     # Canonical production DDL (empty tables, no seed data)
├── seed.dev.sql                   # Isolated development & test fixtures (DO NOT run in prod)
├── migrations/
│   └── 0001_initial_schema.sql   # Cloudflare D1 migration (tables, constraints, indexes)
├── wrangler.toml                  # Cloudflare Workers configuration & bindings
├── package.json                   # Dependencies (Hono, Wrangler, TypeScript)
├── tsconfig.json                  # TypeScript compiler options
└── src/
    ├── index.ts                   # Main entry point, CORS, health check, route mounting
    ├── types.ts                   # TypeScript interfaces (Bindings, JWTPayload, UserRow)
    ├── crypto.ts                  # PBKDF2 password hashing and HS256 JWT utilities
    ├── middleware/
    │   └── auth.ts                # Bearer token verification and RBAC middleware
    └── routes/
        ├── auth.ts                # POST /api/auth/login & POST /api/auth/setup-admin
        └── user.ts                # GET /api/user/profile & GET /api/user/admin/overview
```

---

## 2. Production Database Schema (Cloudflare D1)

The canonical production schema manages 8 relational entities:

1. **`clusters`**: Regional administrative educational clusters.
2. **`schools`**: Schools with unique UDISE codes and cluster mapping.
3. **`users`**: User records with roles (`Admin`, `Cluster_Head`, `School_HM`, `Teacher`) and account status (`active`, `inactive`, `pending`).
4. **`groups`**: Academic and administrative chat/coordination groups.
5. **`group_members`**: Membership mapping users to groups.
6. **`messages`**: Group chat messages with text, image, or document attachment support (`media_url`).
7. **`reports`**: Submitted data reports and Excel spreadsheets stored in R2.
8. **`tasks`**: Data collection assignments, deadlines, and completion statuses.

---

## 3. Step-by-Step Production Setup Guide

### Step 1: Install Dependencies & Verify Build
```bash
cd backend
npm install
npm run build # or npx tsc --noEmit
```

### Step 2: Apply D1 Database Migrations
Wrangler tracks applied migrations in the `d1_migrations` table automatically:

```bash
# Apply migrations to production D1 database
npx wrangler d1 migrations apply kp-data-sync-db --remote
```

*Note:* All tables are created completely empty. No test users or mock data will be inserted.

### Step 3: Configure Cloudflare Worker Secrets
Secrets must **never** be stored in source code or `wrangler.toml`. Set them securely in Cloudflare:

```bash
# 1. Strong secret for signing and validating JWTs (e.g. 64 random characters)
npx wrangler secret put JWT_SECRET

# 2. Strong one-time secret for bootstrapping the first Admin user
npx wrangler secret put SETUP_SECRET
```

*(Alternatively, configure them in Cloudflare Dashboard: **Workers & Pages** → `kp-data-sync-backend` → **Settings** → **Variables** → **Add Secret**).*

### Step 4: Reconcile Cloudflare R2 Bucket Binding
Ensure the Worker has access to Cloudflare R2:
- In the Cloudflare Dashboard under **Workers & Pages** → `kp-data-sync-backend` → **Settings** → **Variables** → **R2 Bucket Bindings**:
  - **Variable Name:** `REPORTS_BUCKET` (must match the code's canonical binding name)
  - **R2 Bucket:** Select your target R2 bucket (e.g., `kp-data-sync` or `kp-data-sync-reports`)

### Step 5: Deploy the Worker
```bash
npx wrangler deploy
```

---

## 4. First-Admin Account Bootstrap

Once deployed and before any admin can log in, run the bootstrap endpoint once using your configured `SETUP_SECRET`.

### Request:
```bash
curl -X POST https://<your-worker-subdomain>.workers.dev/api/auth/setup-admin \
  -H "Content-Type: application/json" \
  -H "X-Setup-Secret: <YOUR_SETUP_SECRET>" \
  -d '{
    "name": "System App Admin",
    "email": "admin@kpdatasync.com",
    "password": "<YOUR_STRONG_ADMIN_PASSWORD>"
  }'
```

### Response (201 Created):
```json
{
  "success": true,
  "message": "Initial Admin user created successfully",
  "user": {
    "id": "c7a840e6-5b4d-4e9f-9c02-e21b8b217a94",
    "name": "System App Admin",
    "email": "admin@kpdatasync.com",
    "role": "Admin",
    "status": "active"
  }
}
```

### Security Safeguards:
- **One-time only:** If an Admin user already exists in D1, the endpoint immediately rejects subsequent calls with `409 Conflict`.
- **Zero plaintext passwords:** The password is immediately hashed with PBKDF2-HMAC-SHA256 with 100,000 iterations before insertion.
- **Timing-safe authentication:** The setup secret is verified using constant-time comparison to prevent side-channel timing attacks.

---

## 5. API Endpoints Reference

### `GET /`
- **Description:** Health check and API metadata.
- **Access:** Public.

### `POST /api/auth/setup-admin`
- **Description:** First-time administrative account creation.
- **Access:** Protected by `SETUP_SECRET` (Header: `X-Setup-Secret`). Only active when 0 Admins exist.

### `POST /api/auth/login`
- **Description:** Authenticate user and issue 7-day HS256 JWT.
- **Body:**
  ```json
  {
    "email": "admin@kpdatasync.com",
    "password": "<admin_password>",
    "role": "Admin" // optional role validation
  }
  ```
- **Responses:**
  - `200 OK`: Returns JWT token and sanitized user profile.
  - `400 Bad Request`: Missing credentials or invalid email format.
  - `401 Unauthorized`: Invalid credentials.
  - `403 Forbidden`: Account is inactive/pending or role does not match.

### `GET /api/user/profile`
- **Description:** Fetch user details with cluster and school associations.
- **Header:** `Authorization: Bearer <token>`
- **Access:** All authenticated roles (`Admin`, `Cluster_Head`, `School_HM`, `Teacher`).

### `GET /api/user/admin/overview`
- **Description:** System statistics (school counts, user counts, reports count).
- **Header:** `Authorization: Bearer <token>`
- **Access:** Restricted to `Admin` and `Cluster_Head`.
