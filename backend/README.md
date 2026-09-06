# KP Data Sync - Cloudflare Backend (Workers + D1 + R2 + Hono.js)

Production-ready backend API for **KP Data Sync (School Data Management App)** providing:
- **Cloudflare D1 (SQLite)** Schema & relational tables for Clusters, Schools, Users, Groups, and Reports.
- **Cloudflare R2** integration for Excel/data files and media storage.
- **Role-Based Access Control (RBAC)** across 4 roles: `Admin`, `Cluster_Head`, `School_HM`, `Teacher`.
- **Hono.js** framework with Web Crypto PBKDF2 password hashing and HS256 JWT tokens.

---

## 1. Directory Structure

```
backend/
├── schema.sql           # D1 SQLite Schema, indexes, & seed data
├── wrangler.toml        # Cloudflare Workers configuration with D1 & R2 bindings
├── package.json         # Dependencies (Hono, @cloudflare/workers-types)
├── tsconfig.json        # TypeScript configuration
└── src/
    ├── index.ts         # Worker main entry point & CORS configuration
    ├── types.ts         # Type definitions (UserRole, Bindings, JWTPayload)
    ├── crypto.ts        # PBKDF2 Web Crypto hashing & HS256 JWT generation
    ├── middleware/
    │   └── auth.ts      # JWT verification & RBAC role enforcement middleware
    └── routes/
        ├── auth.ts      # /api/auth/login
        └── user.ts      # /api/user/profile & /api/user/admin/overview
```

---

## 2. Setup & Deployment Instructions

### Prerequisites
- Node.js (v18+)
- Cloudflare CLI (`npm install -g wrangler`)

### Step 1: Install Dependencies
```bash
cd backend
npm install
```

### Step 2: Create D1 Database & R2 Bucket
```bash
# Create D1 Database
npx wrangler d1 create kp-prod-db

# Create R2 Bucket
npx wrangler r2 bucket create kp-data-sync-reports
```
Copy the generated `database_id` into `wrangler.toml` under `[[d1_databases]]`.

### Step 3: Run Database Migrations / Schema
```bash
# Local development
npx wrangler d1 execute kp-prod-db --local --file=./schema.sql

# Production Cloudflare D1
npx wrangler d1 execute kp-prod-db --remote --file=./schema.sql
```

### Step 4: Configure Secrets & Deploy
```bash
# Set JWT Secret
npx wrangler secret put JWT_SECRET

# Deploy to Cloudflare Edge
npx wrangler deploy
```

---

## 3. Seed Accounts (for testing)

All accounts share the default test password: `Password@123`

| Role | Email | Password | Scope / Permissions |
|------|-------|----------|---------------------|
| **Admin** | `admin@kpdatasync.com` | `Password@123` | System-wide authority, clusters, schools, users, groups |
| **Cluster_Head** | `clusterhead@kpdatasync.com` | `Password@123` | Assigned cluster schools, HMs, teachers, administrative groups |
| **School_HM** | `schoolhm@kpdatasync.com` | `Password@123` | Assigned school, teachers, school group messaging |
| **Teacher** | `teacher@kpdatasync.com` | `Password@123` | Added groups, daily tasks, student data submission |

---

## 4. API Endpoints

### `POST /api/auth/login`
- **Request Body:**
  ```json
  {
    "email": "admin@kpdatasync.com",
    "password": "Password@123",
    "role": "Admin" // optional role validation
  }
  ```
- **Response:**
  ```json
  {
    "success": true,
    "message": "Login successful",
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "user": {
      "id": "user-admin-01",
      "name": "System App Admin",
      "email": "admin@kpdatasync.com",
      "role": "Admin",
      "cluster_id": null,
      "school_id": null,
      "status": "active"
    }
  }
  ```

### `GET /api/user/profile`
- **Header:** `Authorization: Bearer <token>`
- **Access:** Allowed for all authenticated roles.

### `GET /api/user/admin/overview`
- **Header:** `Authorization: Bearer <token>`
- **Access:** Allowed only for `Admin` and `Cluster_Head`.
