# Auth Service

The **Auth Service** (`com.subdual.auth_service`) owns user identity, credential security, and JWT token issuance for the platform.

---

## 1. Core Responsibilities

* **Core Principle**: *"Auth Service owns user identity, credential security, and JWT lifecycle."*
* **Credential Security**: Hashes user passwords using BCrypt with a work factor of 10.
* **Token Issuance**: Generates signed HMAC-SHA256 JSON Web Tokens (JWT) containing `userId` and `email` claims.
* **Relational Storage**: Manages the `users` table using versioned Flyway migrations.

---

## 2. Key Capabilities & Endpoints

| Method | Path | Description | Public / Auth |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Registers a new user account, hashes password, and returns a signed JWT. | Public |
| `POST` | `/api/v1/auth/login` | Validates credentials against stored BCrypt hash and returns a signed JWT. | Public |
| `GET` | `/api/v1/auth/me` | Returns the currently authenticated user's profile details. | Auth (JWT) |
| `GET` | `/actuator/health` | Service health probe (`UP`). | Public |

---

## 3. Relational Schema (`users` table)

```sql
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email)
);
```

---

## 4. JWT Architecture

* **Algorithm**: HMAC-SHA256 (`HS256`).
* **Secret**: Configured via `JWT_SECRET` (or `jwt.secret` property).
* **Expiration**: Configurable (default: 86,400 seconds / 24 hours).
* **Claims**:
  - `sub`: User email address.
  - `userId`: Canonical UUID identifier of the user record.
  - `name`: User display name.
  - `iat` and `exp`: Timestamp claims.
