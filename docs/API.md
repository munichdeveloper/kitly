# API Documentation

## Base URL

- **Development**: `http://localhost:8080/api`
- **Production**: `https://your-domain.com/api`

## Authentication

Most endpoints require authentication via JWT token in the Authorization header:

```
Authorization: Bearer <your-jwt-token>
```

## Response Format

### Success Response
```json
{
  "data": { /* response data */ },
  "timestamp": "2025-12-19T12:00:00Z"
}
```

### Error Response
```json
{
  "timestamp": "2025-12-19T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message",
  "path": "/api/endpoint"
}
```

## API Endpoints

### Authentication

#### POST /api/auth/signup
Register a new user account.

**Request:**
```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "SecurePassword123!",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Response:** `200 OK`
```json
{
  "token": "eyJhbGc...",
  "type": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "id": "uuid",
    "username": "johndoe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe"
  }
}
```

#### POST /api/auth/login
Authenticate and receive JWT token.

**Request:**
```json
{
  "username": "johndoe",
  "password": "SecurePassword123!"
}
```

**Response:** `200 OK`
```json
{
  "token": "eyJhbGc...",
  "type": "Bearer",
  "expiresIn": 86400000
}
```

---

### Tenants

#### POST /api/tenants
Create a new tenant (organization).

**Auth Required:** Yes

**Request:**
```json
{
  "name": "Acme Corporation",
  "slug": "acme-corp",
  "domain": "acme.example.com"
}
```

**Response:** `201 Created`
```json
{
  "id": "uuid",
  "name": "Acme Corporation",
  "slug": "acme-corp",
  "domain": "acme.example.com",
  "status": "ACTIVE",
  "ownerId": "uuid",
  "createdAt": "2025-12-19T12:00:00Z",
  "updatedAt": "2025-12-19T12:00:00Z"
}
```

**Notes:**
- Automatically creates OWNER membership for the current user
- Creates default FREE subscription with TRIALING status
- Slug must be unique across all tenants

#### GET /api/tenants/me
Get all tenants the current user belongs to.

**Auth Required:** Yes

**Response:** `200 OK`
```json
[
  {
    "id": "uuid",
    "name": "Acme Corporation",
    "slug": "acme-corp",
    "domain": "acme.example.com",
    "status": "ACTIVE",
    "ownerId": "uuid",
    "createdAt": "2025-12-19T12:00:00Z",
    "updatedAt": "2025-12-19T12:00:00Z"
  }
]
```

#### GET /api/tenants/{tenantId}
Get tenant details by ID.

**Auth Required:** Yes (must be member of tenant)

**Response:** `200 OK`
```json
{
  "id": "uuid",
  "name": "Acme Corporation",
  "slug": "acme-corp",
  "domain": "acme.example.com",
  "status": "ACTIVE",
  "ownerId": "uuid",
  "createdAt": "2025-12-19T12:00:00Z",
  "updatedAt": "2025-12-19T12:00:00Z"
}
```

---

### Memberships

#### GET /api/tenants/{tenantId}/members
List all members of a tenant.

**Auth Required:** Yes (must be member of tenant)

**Response:** `200 OK`
```json
[
  {
    "id": "uuid",
    "userId": "uuid",
    "username": "johndoe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "role": "OWNER",
    "status": "ACTIVE",
    "joinedAt": "2025-12-19T12:00:00Z"
  }
]
```

#### PUT /api/tenants/{tenantId}/members/{userId}
Update a member's role or status.

**Auth Required:** Yes (OWNER or ADMIN role required)

**Request:**
```json
{
  "role": "ADMIN",
  "status": "ACTIVE"
}
```

**Response:** `200 OK`
```json
{
  "id": "uuid",
  "userId": "uuid",
  "username": "janedoe",
  "email": "jane@example.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "role": "ADMIN",
  "status": "ACTIVE",
  "joinedAt": "2025-12-19T12:00:00Z"
}
```

**Notes:**
- Cannot change the last OWNER's role
- Entitlement version is bumped on role change

---

### Invitations

#### POST /api/tenants/{tenantId}/invites
Create an invitation to join the tenant.

**Auth Required:** Yes (OWNER or ADMIN role required)

**Request:**
```json
{
  "email": "newuser@example.com",
  "role": "MEMBER"
}
```

**Response:** `201 Created`
```json
{
  "id": "uuid",
  "tenantId": "uuid",
  "email": "newuser@example.com",
  "role": "MEMBER",
  "token": "random-secure-token",
  "expiresAt": "2025-12-26T12:00:00Z"
}
```

**Notes:**
- Token is only returned once and should be sent to the invitee
- Email with invitation link is sent automatically
- Checks that user is not already a member
- Verifies no pending invitation exists for the email

#### GET /api/tenants/{tenantId}/invites
List pending invitations for a tenant.

**Auth Required:** Yes (OWNER or ADMIN role required)

**Response:** `200 OK`
```json
[
  {
    "id": "uuid",
    "tenantId": "uuid",
    "teamId": null,
    "email": "newuser@example.com",
    "role": "MEMBER",
    "status": "PENDING",
    "invitedByUsername": "johndoe",
    "invitedAt": "2025-12-19T12:00:00Z",
    "expiresAt": "2025-12-26T12:00:00Z",
    "acceptedAt": null
  }
]
```

#### POST /api/invites/accept
Accept an invitation and create membership.

**Auth Required:** No (public endpoint)

**Request:**
```json
{
  "token": "random-secure-token"
}
```

**Response:** `200 OK`
```json
{
  "message": "Invitation accepted successfully",
  "tenantId": "uuid",
  "userId": "uuid"
}
```

**Notes:**
- Auto-provisions user if email doesn't exist
- Checks seat limits before accepting
- Marks invitation as ACCEPTED
- Creates ACTIVE membership
- Bumps entitlement version

---

### Session Management

#### POST /api/session/switch-tenant
Switch current session to a different tenant.

**Auth Required:** Yes

**Request:**
```json
{
  "tenantId": "uuid"
}
```

**Response:** `200 OK`
```json
{
  "token": "eyJhbGc...",
  "type": "Bearer",
  "userId": "uuid",
  "tenantId": "uuid",
  "roles": ["MEMBER"],
  "entitlementVersion": 5,
  "expiresIn": 900000
}
```

**Notes:**
- Validates user has active membership in tenant
- Generates new JWT with tenant context
- Includes entitlement version for caching

#### POST /api/session/refresh
Refresh the current session token.

**Auth Required:** Yes

**Request:**
```json
{
  "token": "current-session-token"
}
```

**Response:** `200 OK`
```json
{
  "token": "eyJhbGc...",
  "type": "Bearer",
  "expiresIn": 900000
}
```

#### GET /api/session/current
Get current session information.

**Auth Required:** Yes

**Response:** `200 OK`
```json
{
  "userId": "uuid",
  "username": "johndoe",
  "tenantId": "uuid",
  "tenantName": "Acme Corporation",
  "roles": ["OWNER"],
  "entitlementVersion": 5
}
```

---

### Entitlements

#### GET /api/plans
Get catalog of available subscription plans.

**Auth Required:** No

**Response:** `200 OK`
```json
{
  "starter": {
    "code": "starter",
    "name": "Starter",
    "entitlements": {
      "features.ai_assistant": "false",
      "limits.projects": "10",
      "limits.api_calls_per_month": "1000"
    }
  },
  "pro": {
    "code": "pro",
    "name": "Professional",
    "entitlements": {
      "features.ai_assistant": "true",
      "limits.projects": "100",
      "limits.api_calls_per_month": "50000"
    }
  },
  "enterprise": {
    "code": "enterprise",
    "name": "Enterprise",
    "entitlements": {
      "features.ai_assistant": "true",
      "limits.projects": "unlimited",
      "limits.api_calls_per_month": "unlimited"
    }
  }
}
```

#### GET /api/tenants/{tenantId}/entitlements
Get computed entitlements for a tenant.

**Auth Required:** Yes (must be member of tenant)

**Response:** `200 OK`
```json
{
  "tenantId": "uuid",
  "planCode": "pro",
  "status": "ACTIVE",
  "seatsQuantity": 50,
  "activeSeats": 7,
  "entitlementVersion": 15,
  "items": [
    {
      "key": "features.ai_assistant",
      "value": "true",
      "source": "PLAN"
    },
    {
      "key": "limits.projects",
      "value": "100",
      "source": "PLAN"
    },
    {
      "key": "limits.api_calls_per_month",
      "value": "50000",
      "source": "PLAN"
    }
  ]
}
```

**Notes:**
- Merges entitlements from PLAN → ADDON → OVERRIDE
- Includes current seat usage
- Version number for cache invalidation

#### GET /api/entitlements/me
Get entitlements for current tenant context.

**Auth Required:** Yes (tenant context required)

**Response:** Same as above

**Notes:**
- Uses tenant ID from JWT token
- Returns 400 if no tenant context in token

---

### Users

#### GET /api/users/me
Get current user profile.

**Auth Required:** Yes

**Response:** `200 OK`
```json
{
  "id": "uuid",
  "username": "johndoe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "isActive": true,
  "createdAt": "2025-12-19T12:00:00Z",
  "updatedAt": "2025-12-19T12:00:00Z"
}
```

---

### Health Check

#### GET /api/health
Application health status.

**Auth Required:** No

**Response:** `200 OK`
```json
{
  "status": "UP",
  "timestamp": "2025-12-19T12:00:00Z"
}
```

---

## Error Codes

| Status Code | Description |
|-------------|-------------|
| 200 | Success |
| 201 | Created |
| 400 | Bad Request - Invalid input |
| 401 | Unauthorized - Authentication required |
| 403 | Forbidden - Insufficient permissions |
| 404 | Not Found - Resource doesn't exist |
| 409 | Conflict - Resource already exists |
| 500 | Internal Server Error |

## Rate Limiting

Currently not implemented. Consider implementing in production:
- 100 requests per minute per IP
- 1000 requests per hour per authenticated user

## Pagination

Currently not implemented for most endpoints. Future enhancement:

```
GET /api/tenants/{tenantId}/members?page=1&size=20
```

Response would include:
```json
{
  "content": [ /* items */ ],
  "page": 1,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8
}
```

## Webhooks (Future)

Planned webhook events:
- `tenant.created`
- `member.added`
- `member.removed`
- `subscription.updated`
- `invitation.accepted`
- `entitlement.changed`

## OIDC Integration (Future)

Support for OAuth2/OIDC providers:
- Keycloak
- Auth0
- Okta
- Google OAuth
- GitHub OAuth
