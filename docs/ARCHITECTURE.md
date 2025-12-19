# Kitly Architecture Documentation

## System Overview

Kitly is a modern, production-ready SaaS platform built with a Spring Boot backend and Next.js frontend. The platform provides comprehensive multi-tenancy support, subscription management, team collaboration, and fine-grained entitlements.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         Frontend Layer                       │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │  Next.js   │  │  React 19  │  │ Tailwind  │             │
│  │  App       │  │  UI Layer  │  │    CSS    │             │
│  └────────────┘  └────────────┘  └────────────┘            │
└─────────────────────────────────────────────────────────────┘
                            ↓ HTTP/REST
┌─────────────────────────────────────────────────────────────┐
│                       API Gateway Layer                      │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Spring Security + JWT Authentication                  │ │
│  │  CORS Configuration                                     │ │
│  │  Tenant Context Filter                                 │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                     Application Layer                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────┐  │
│  │ Tenant   │  │  Auth    │  │  Team    │  │Entitlement│  │
│  │ Service  │  │ Service  │  │ Service  │  │  Service  │  │
│  └──────────┘  └──────────┘  └──────────┘  └───────────┘  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│  │Membership│  │  Invite  │  │ Session  │                  │
│  │ Service  │  │ Service  │  │ Service  │                  │
│  └──────────┘  └──────────┘  └──────────┘                 │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                      Data Layer                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Spring Data JPA + PostgreSQL                          │ │
│  │  Flyway Migrations                                     │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Authentication & Authorization

**JWT-Based Authentication**
- User authentication with username/password
- JWT tokens for stateless authentication
- Refresh token mechanism for session management
- Role-based access control (RBAC)

**Tenant Context Management**
- Session tokens contain tenant context
- Automatic tenant isolation in database queries
- TenantContextHolder for thread-local tenant access

**Security Features**
- Password encryption with BCrypt
- CORS configuration for cross-origin requests
- Method-level security annotations
- API endpoint protection

### 2. Multi-Tenancy

**Tenant Model**
- Each tenant represents an organization/company
- Tenant has a unique slug and optional domain
- Owner relationship to establish primary administrator
- Status management (ACTIVE, SUSPENDED, INACTIVE)

**Membership System**
- Users can belong to multiple tenants
- Three role levels: OWNER, ADMIN, MEMBER
- Membership status: ACTIVE, INACTIVE, SUSPENDED
- Automatic OWNER membership on tenant creation

**Data Isolation**
- Database-level tenant isolation
- All tenant-scoped queries filter by tenant_id
- TenantContextFilter ensures proper context
- No cross-tenant data leakage

### 3. Subscription & Entitlements

**Subscription Management**
- Multiple plans: FREE, STARTER, PROFESSIONAL, ENTERPRISE
- Subscription status: ACTIVE, TRIALING, CANCELLED, EXPIRED, PAST_DUE
- Seat-based licensing with configurable limits
- Trial period support

**Entitlement System**
- Hierarchical entitlement merging: PLAN → ADDON → OVERRIDE
- Feature flags, limits, and quotas
- Version-based cache invalidation
- Real-time entitlement computation

**Version Bumping**
- EntitlementVersion tracks changes
- Automatic version bump on:
  - Subscription updates
  - Membership changes
  - Manual entitlement overrides
- Event-driven architecture for version updates

### 4. Team Collaboration

**Invitation System**
- Email-based invitations
- Secure token generation (SHA-256 hashing)
- Token expiration handling
- Auto-user provisioning on invite acceptance

**Member Management**
- Role assignment and updates
- Member activation/deactivation
- Seat limit enforcement
- Activity tracking

### 5. Session Management

**Tenant-Scoped Sessions**
- JWT tokens with tenant context
- Session refresh mechanism
- Current session introspection
- Tenant switching capability

## Data Model

### Core Entities

**User**
- Identity and authentication
- Can belong to multiple tenants
- Has system-level roles

**Tenant**
- Organization/company entity
- Owns subscriptions and members
- Has a designated owner

**Membership**
- Links users to tenants
- Defines role within tenant
- Manages member status

**Subscription**
- Tenant's billing plan
- Seat limits and pricing
- Trial and lifecycle management

**Invitation**
- Pending member invites
- Tokenized for security
- Tracks invite lifecycle

**EntitlementVersion**
- Version tracking for entitlements
- Enables client-side caching
- Incremented on changes

### Database Schema Relationships

```
users ──────┐
            │
            ├──< memberships >──── tenants ────┐
            │                                   │
            │                                   ├──< subscriptions
            │                                   │
            └──< invitations >──────────────────┘
                                               │
                                               └──< entitlement_versions
                                               │
                                               └──< entitlements
```

## Technology Stack

### Backend
- **Framework**: Spring Boot 4.0.1
- **Language**: Java 17
- **Database**: PostgreSQL 15
- **ORM**: Hibernate + Spring Data JPA
- **Migrations**: Flyway
- **Security**: Spring Security + JWT (JJWT)
- **Build**: Maven

### Frontend
- **Framework**: Next.js 16.1.0
- **UI Library**: React 19
- **Language**: TypeScript
- **Styling**: Tailwind CSS
- **State Management**: React Context API

### Infrastructure
- **Containerization**: Docker + Docker Compose
- **Database**: PostgreSQL (production), H2 (dev)
- **Testing**: JUnit 5, Mockito, Testcontainers

## Security Architecture

### Authentication Flow

1. User submits credentials to `/api/auth/login`
2. Backend validates credentials
3. JWT token generated with user claims
4. Token returned to client
5. Client includes token in Authorization header
6. Backend validates token on each request

### Tenant Context Flow

1. User selects tenant via `/api/session/switch-tenant`
2. Backend validates user membership
3. New JWT token generated with tenant context
4. TenantContextFilter extracts tenant ID from token
5. All queries automatically scoped to tenant

### Authorization Layers

1. **Method Level**: `@PreAuthorize` annotations
2. **Service Level**: Manual permission checks
3. **Data Level**: Automatic tenant filtering
4. **API Level**: Spring Security filter chain

## Scalability Considerations

### Current Architecture
- Stateless API (JWT-based)
- Database connection pooling
- Transaction management
- Event-driven version updates

### Future Enhancements
1. **Caching Layer**: Redis for entitlements and sessions
2. **Message Queue**: RabbitMQ/Kafka for async operations
3. **Load Balancing**: Multiple backend instances
4. **CDN**: Static asset distribution
5. **Monitoring**: Prometheus + Grafana
6. **Logging**: ELK Stack

## Development Patterns

### Service Layer
- Business logic encapsulation
- Transaction management
- Event publishing for cross-cutting concerns

### Repository Layer
- Spring Data JPA repositories
- Custom query methods
- Optimized database access

### DTO Pattern
- Request/Response DTOs for API contracts
- Entity-to-DTO mapping in services
- Input validation with Jakarta Validation

### Builder Pattern
- Entity builders for testing
- Fluent API for test data creation

## Deployment Architecture

### Development
```
Docker Compose:
- PostgreSQL container
- Backend container (Spring Boot)
- Frontend container (Next.js dev server)
```

### Production (Recommended)
```
Kubernetes Cluster:
- PostgreSQL (managed service or StatefulSet)
- Backend (Deployment + Service)
- Frontend (Deployment + Service + Ingress)
- Optional: Keycloak for OIDC
```

## Monitoring & Observability

### Logging
- Structured logging with SLF4J
- Log levels: DEBUG, INFO, WARN, ERROR
- Tenant ID included in log context

### Health Checks
- `/api/health` endpoint
- Database connectivity check
- Application startup verification

### Metrics (Future)
- Request rates and latencies
- Database query performance
- JVM metrics
- Business metrics (tenant count, active users)

## Error Handling

### Exception Hierarchy
- `ResourceNotFoundException` - 404 errors
- `BadRequestException` - 400 errors  
- `UnauthorizedException` - 401/403 errors
- `GlobalExceptionHandler` - Centralized error handling

### API Error Response Format
```json
{
  "timestamp": "2025-12-19T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Seat limit reached",
  "path": "/api/tenants/123/invites"
}
```

## Testing Strategy

### Unit Tests
- Service layer logic
- Utility functions
- Isolated component testing

### Integration Tests
- Full Spring context
- Testcontainers for PostgreSQL
- End-to-end business flows
- Database transactions

### API Contract Tests
- MockMvc for endpoint testing
- Request/Response validation
- Authentication and authorization
- Error scenario coverage

## Configuration Management

### Application Profiles
- `dev` - Local development with H2
- `test` - Integration testing
- `prod` - Production with PostgreSQL

### Environment Variables
- Database credentials
- JWT secrets
- CORS origins
- OIDC configuration

## API Versioning

Currently: No versioning (v1 implicit)
Future: `/api/v2/` path-based versioning

## Appendix: Key Design Decisions

1. **JWT over Sessions**: Stateless, scalable
2. **PostgreSQL over NoSQL**: ACID compliance, complex queries
3. **Flyway over Liquibase**: Simplicity, SQL-based
4. **Event-driven version bumping**: Loose coupling, testability
5. **Testcontainers**: Real database in tests, confidence
