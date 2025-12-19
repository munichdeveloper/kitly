# Quick Start Guide

Get your Kitly SaaS platform running in under 5 minutes!

## Prerequisites

- **Java 17+** ([Download](https://adoptium.net/))
- **Maven 3.6+** ([Download](https://maven.apache.org/))
- **Node.js 18+** ([Download](https://nodejs.org/))
- **PostgreSQL 15** (or Docker) ([Download](https://www.postgresql.org/))

## Quick Start (5 Minutes)

### Option 1: Docker Compose (Recommended) ⚡

```bash
# 1. Clone the repository
git clone https://github.com/munichdeveloper/kitly.git
cd kitly

# 2. Start all services
docker-compose up

# That's it! 🎉
```

**Access the application:**
- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080/api
- **Database**: PostgreSQL on localhost:5432

### Option 2: Local Development

#### Step 1: Setup Database (1 minute)

```bash
# Option A: Using PostgreSQL CLI
createdb kitlydb

# Option B: Using psql
psql -U postgres
CREATE DATABASE kitlydb;
\q
```

#### Step 2: Start Backend (2 minutes)

```bash
cd backend

# Configure environment (copy and edit as needed)
cp .env.example .env

# Build and run
mvn clean install
mvn spring-boot:run

# Backend running at http://localhost:8080/api
```

#### Step 3: Start Frontend (2 minutes)

```bash
# In a new terminal
cd frontend

# Configure environment
cp .env.example .env.local

# Install and run
npm install
npm run dev

# Frontend running at http://localhost:3000
```

## First Steps

### 1. Create Your First Account

1. Open http://localhost:3000
2. Click **"Sign Up"**
3. Fill in your details:
   - Username: `johndoe`
   - Email: `john@example.com`
   - Password: `SecurePassword123!`
   - First Name: `John`
   - Last Name: `Doe`
4. Click **"Sign Up"**

You'll be automatically logged in! 🎉

### 2. Create Your First Tenant (Organization)

```bash
# Using curl
curl -X POST http://localhost:8080/api/tenants \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Company",
    "slug": "my-company",
    "domain": "mycompany.com"
  }'
```

This automatically:
- Creates an OWNER membership for you
- Sets up a FREE subscription with 14-day trial
- Initializes default entitlements

### 3. Invite Team Members

```bash
curl -X POST http://localhost:8080/api/tenants/{tenantId}/invites \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "teammate@example.com",
    "role": "MEMBER"
  }'
```

The invitation email will be logged (dev mode) with the invite token.

### 4. Switch Tenant Context

```bash
curl -X POST http://localhost:8080/api/session/switch-tenant \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "YOUR_TENANT_ID"
  }'
```

You'll receive a new session token with tenant context.

## API Endpoints

### Authentication

```bash
# Sign Up
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "email": "john@example.com",
    "password": "SecurePassword123!",
    "firstName": "John",
    "lastName": "Doe"
  }'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "password": "SecurePassword123!"
  }'
```

### Health Check

```bash
curl http://localhost:8080/api/health
```

Response:
```json
{
  "status": "UP",
  "timestamp": "2025-12-19T12:00:00Z"
}
```

### Get Current User

```bash
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Get Entitlements

```bash
curl http://localhost:8080/api/tenants/{tenantId}/entitlements \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

For complete API documentation, see [docs/API.md](docs/API.md).

## Testing the Platform

### Run Backend Tests

```bash
cd backend

# All tests
mvn test

# Integration tests only
mvn test -Dtest=*IntegrationTest

# With coverage
mvn test jacoco:report
```

### Manual Testing Checklist

- [ ] Sign up new user
- [ ] Login with credentials
- [ ] Create tenant
- [ ] Invite member (check logs for token)
- [ ] Accept invite
- [ ] Check member list
- [ ] Switch between tenants
- [ ] View entitlements
- [ ] Update member role
- [ ] Check seat count

## Configuration Files

### Backend (.env)

Key settings in `backend/.env`:

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/kitlydb
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# JWT Secrets (CHANGE IN PRODUCTION!)
JWT_SECRET=your-production-secret-here
JWT_SESSION_SECRET=your-session-secret-here

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

### Frontend (.env.local)

Key settings in `frontend/.env.local`:

```bash
# API Configuration
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

## Troubleshooting

### Backend Won't Start

**Problem**: Port 8080 already in use
```bash
# Find and kill process
lsof -i :8080
kill -9 <PID>
```

**Problem**: Database connection fails
```bash
# Check PostgreSQL is running
pg_isready -h localhost -p 5432

# Verify database exists
psql -U postgres -l | grep kitlydb
```

**Problem**: Flyway migration fails
```bash
# Reset database (CAUTION: deletes data)
mvn flyway:clean
mvn flyway:migrate
```

### Frontend Won't Start

**Problem**: Module not found
```bash
# Clear and reinstall
rm -rf node_modules package-lock.json
npm install
```

**Problem**: API calls fail (CORS)
- Check backend CORS configuration
- Verify `NEXT_PUBLIC_API_URL` in `.env.local`
- Check browser console for errors

### Docker Issues

**Problem**: Containers won't start
```bash
# Check logs
docker-compose logs

# Rebuild images
docker-compose build --no-cache
docker-compose up
```

**Problem**: Database data persists
```bash
# Remove volumes
docker-compose down -v
docker-compose up
```

## What's Next?

After setup, explore:

1. **API Documentation**: Read [docs/API.md](docs/API.md) for all endpoints
2. **Architecture**: Understand the system in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
3. **Development Guide**: Full setup in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)
4. **Deployment**: Production deployment in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)

## Key Features to Try

### Multi-Tenancy
- Create multiple tenants
- Switch between tenant contexts
- Verify data isolation

### Team Collaboration
- Invite members via email
- Assign different roles (OWNER, ADMIN, MEMBER)
- Test seat limits

### Subscriptions
- Check default FREE plan (3 seats)
- View entitlements per plan
- Monitor active seat usage

### Entitlements
- View plan features and limits
- Test entitlement version tracking
- Observe version bumps on changes

## Common Commands

```bash
# Backend
mvn clean install          # Build
mvn spring-boot:run        # Run
mvn test                   # Test
mvn flyway:migrate         # Run migrations
mvn flyway:info           # Migration status

# Frontend
npm install                # Install dependencies
npm run dev               # Development server
npm run build             # Production build
npm start                 # Production server
npm run lint              # Lint code

# Docker
docker-compose up         # Start all services
docker-compose down       # Stop all services
docker-compose logs -f    # View logs
docker-compose ps         # Check status
```

## Getting Help

- 📖 [Full Documentation](docs/)
- 💬 [GitHub Discussions](https://github.com/munichdeveloper/kitly/discussions)
- 🐛 [Report Issues](https://github.com/munichdeveloper/kitly/issues)
- 📧 Contact: support@kitly.com

## Production Checklist

Before deploying to production:

- [ ] Change all default passwords
- [ ] Generate strong JWT secrets (min 256 bits)
- [ ] Enable HTTPS/TLS
- [ ] Configure proper CORS origins
- [ ] Set up database backups
- [ ] Enable monitoring and logging
- [ ] Review security settings
- [ ] Test disaster recovery

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for complete production deployment guide.

---

**Ready to build your SaaS! 🚀**

*For detailed setup instructions, see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)*
