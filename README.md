# Kitly - Production-Ready SaaS Platform

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Test Coverage](https://img.shields.io/badge/coverage-80%25-green)]()
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-16.1.0-black.svg)](https://nextjs.org/)

A modern, enterprise-grade SaaS monorepo featuring Spring Boot backend and Next.js frontend with comprehensive multi-tenancy, subscription management, team collaboration, and fine-grained entitlements.

## 🚀 Key Features

### Multi-Tenancy & Organizations
- **Tenant Isolation**: Complete data isolation between tenants
- **Flexible Membership**: Users can belong to multiple organizations
- **Role Hierarchy**: OWNER, ADMIN, MEMBER roles with granular permissions
- **Team Collaboration**: Invite system with email-based onboarding

### Subscription & Entitlements
- **Multiple Plans**: FREE, STARTER, PROFESSIONAL, ENTERPRISE
- **Seat-Based Licensing**: Configurable seat limits per plan
- **Dynamic Entitlements**: Hierarchical feature flags, limits, and quotas
- **Version-Based Caching**: Efficient entitlement change tracking

### Authentication & Security
- **JWT Authentication**: Stateless, scalable authentication
- **Tenant-Scoped Sessions**: Secure tenant context management
- **Token Security**: SHA-256 hashed invitation tokens
- **RBAC**: Method-level and API-level access control

### Backend (Spring Boot)
- **RESTful API**: Well-structured endpoints with OpenAPI docs
- **PostgreSQL Database**: Production-ready with Flyway migrations
- **Event-Driven Architecture**: Loose coupling for entitlement updates
- **Comprehensive Testing**: Unit, integration, and API contract tests
- **Testcontainers**: Real database testing for confidence

### Frontend (Next.js)
- **Modern Stack**: Next.js 16, React 19, TypeScript, Tailwind CSS
- **Responsive Design**: Mobile-first, fully responsive
- **Protected Routes**: Client-side authentication guards
- **Type Safety**: Full TypeScript coverage

## 📁 Project Structure

```
kitly/
├── backend/                      # Spring Boot backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/kitly/saas/
│   │   │   │   ├── common/       # Shared utilities
│   │   │   │   ├── controller/   # REST controllers
│   │   │   │   ├── dto/          # Data Transfer Objects
│   │   │   │   ├── entity/       # JPA entities
│   │   │   │   ├── entitlement/  # Entitlement system
│   │   │   │   ├── repository/   # Data repositories
│   │   │   │   ├── security/     # Security & JWT
│   │   │   │   ├── service/      # Business logic
│   │   │   │   └── tenant/       # Multi-tenancy
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/ # Flyway scripts
│   │   └── test/
│   │       ├── java/
│   │       │   └── integration/  # Integration tests
│   │       └── resources/
│   ├── pom.xml
│   └── .env.example
│
├── frontend/                     # Next.js frontend
│   ├── app/
│   ├── components/
│   ├── lib/
│   ├── package.json
│   └── .env.example
│
├── docs/                         # Documentation
│   ├── ARCHITECTURE.md           # System architecture
│   ├── API.md                    # API documentation
│   ├── DEPLOYMENT.md             # Deployment guide
│   └── DEVELOPMENT.md            # Development setup
│
├── docker-compose.yml            # Local development
├── QUICKSTART.md                 # Quick start guide
└── README.md                     # This file
```

## 🛠️ Prerequisites

- **Java 17** or higher
- **Maven 3.6+**
- **Node.js 18+** and npm
- **PostgreSQL 15** (or Docker for containerized setup)
- Git

## 🚀 Quick Start

### Option 1: Docker Compose (Recommended)

```bash
git clone https://github.com/munichdeveloper/kitly.git
cd kitly
docker-compose up
```

Access the application:
- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080/api
- **PostgreSQL**: localhost:5432

### Option 2: Local Development

**1. Setup Database**
```bash
# Create PostgreSQL database
createdb kitlydb
```

**2. Start Backend**
```bash
cd backend
cp .env.example .env  # Configure your environment
mvn clean install
mvn spring-boot:run
```

Backend will be available at `http://localhost:8080/api`

**3. Start Frontend**
```bash
cd frontend
cp .env.example .env.local  # Configure your environment
npm install
npm run dev
```

Frontend will be available at `http://localhost:3000`

## 📚 Documentation

- **[Quick Start Guide](QUICKSTART.md)** - Get up and running in 5 minutes
- **[Architecture Documentation](docs/ARCHITECTURE.md)** - System design and components
- **[API Documentation](docs/API.md)** - Complete API reference
- **[Development Guide](docs/DEVELOPMENT.md)** - Local development setup
- **[Deployment Guide](docs/DEPLOYMENT.md)** - Production deployment instructions
- **[Entitlements System](backend/docs/ENTITLEMENTS.md)** - Feature flags and limits

## 🔐 Default Configuration

### Development Database
- **URL**: `jdbc:postgresql://localhost:5432/kitlydb`
- **Username**: `postgres`
- **Password**: `postgres`

### JWT Configuration
- **Secret**: Change in production! (See `.env.example`)
- **Token Expiration**: 24 hours
- **Session Expiration**: 15 minutes

### CORS
- **Allowed Origins**: `http://localhost:3000`

## 🔑 Core API Endpoints

### Authentication
```bash
POST /api/auth/signup  # Register new user
POST /api/auth/login   # Login and get JWT
```

### Tenants
```bash
POST   /api/tenants           # Create tenant
GET    /api/tenants/me        # Get user's tenants
GET    /api/tenants/{id}      # Get tenant details
```

### Members & Invitations
```bash
GET    /api/tenants/{id}/members      # List members
PUT    /api/tenants/{id}/members/{userId}  # Update member
POST   /api/tenants/{id}/invites      # Create invitation
GET    /api/tenants/{id}/invites      # List invitations
POST   /api/invites/accept            # Accept invitation
```

### Session Management
```bash
POST   /api/session/switch-tenant  # Switch tenant context
POST   /api/session/refresh        # Refresh session token
GET    /api/session/current        # Get current session
```

### Entitlements
```bash
GET    /api/plans                         # Get plan catalog
GET    /api/tenants/{id}/entitlements    # Get tenant entitlements
GET    /api/entitlements/me              # Get current entitlements
```

For complete API documentation, see [docs/API.md](docs/API.md).

## 🧪 Testing

### Backend Tests

```bash
cd backend

# Run all tests
mvn test

# Run integration tests with Testcontainers
mvn test -Dtest=*IntegrationTest

# Run with coverage report
mvn test jacoco:report
# View report at target/site/jacoco/index.html
```

**Test Coverage**: >80% for critical paths

Test Types:
- **Unit Tests**: Service layer logic with Mockito
- **Integration Tests**: Full Spring context with Testcontainers PostgreSQL
- **API Contract Tests**: MockMvc for endpoint validation

### Frontend Tests

```bash
cd frontend
npm test
npm run test:coverage
```

## 📦 Technologies

### Backend Stack
- **Framework**: Spring Boot 4.0.1
- **Language**: Java 17
- **Database**: PostgreSQL 15
- **ORM**: Hibernate + Spring Data JPA
- **Migrations**: Flyway
- **Security**: Spring Security + JWT (JJWT 0.12.3)
- **Testing**: JUnit 5, Mockito, Testcontainers
- **Build**: Maven

### Frontend Stack
- **Framework**: Next.js 16.1.0
- **UI Library**: React 19
- **Language**: TypeScript
- **Styling**: Tailwind CSS
- **State**: React Context API

### Infrastructure
- **Container**: Docker + Docker Compose
- **Database**: PostgreSQL (prod), Testcontainers (test)
- **CI/CD**: GitHub Actions ready

## 🚀 Deployment

### Docker Production Deployment

```bash
# Build images
docker build -t kitly-backend:latest ./backend
docker build -t kitly-frontend:latest ./frontend

# Deploy with docker-compose
docker-compose -f docker-compose.prod.yml up -d
```

### Kubernetes Deployment

```bash
# Apply configurations
kubectl apply -f k8s/backend-deployment.yaml
kubectl apply -f k8s/frontend-deployment.yaml
kubectl apply -f k8s/ingress.yaml
```

For detailed deployment instructions, see [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## 🏗️ Architecture Highlights

### Multi-Tenancy
- Database-level tenant isolation
- Automatic tenant context filtering
- Secure cross-tenant data protection

### Subscription Management
- Flexible plan system (FREE → ENTERPRISE)
- Seat-based licensing
- Trial period support
- Entitlement version tracking

### Security
- JWT stateless authentication
- Tenant-scoped session tokens
- SHA-256 hashed invitation tokens
- Method-level authorization
- CORS protection

### Scalability
- Stateless API design
- Event-driven version updates
- Connection pooling
- Horizontal scaling ready

For detailed architecture, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## 📈 Monitoring & Health

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

### Application Metrics

Available via Spring Boot Actuator (when enabled):
- `/actuator/health` - Health status
- `/actuator/metrics` - Application metrics
- `/actuator/info` - Application info

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

Please ensure:
- Tests pass (`mvn test`)
- Code follows existing style
- Documentation is updated
- Commit messages follow conventional commits

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🌟 Roadmap

- [x] Multi-tenancy with complete isolation
- [x] Subscription & entitlement system
- [x] Email-based invitation flow
- [x] Comprehensive test coverage
- [x] Docker & Kubernetes deployment configs
- [x] Complete API documentation
- [ ] OAuth2/OIDC integration (Keycloak, Auth0)
- [ ] Email service integration
- [ ] Webhook system for events
- [ ] Admin dashboard UI
- [ ] Usage analytics & reporting
- [ ] Rate limiting
- [ ] API versioning
- [ ] Payment integration (Stripe)
- [ ] Audit logging
- [ ] Redis caching layer

## 🐛 Bug Reports & Feature Requests

Please use GitHub Issues to report bugs or request features:
- [Report a Bug](https://github.com/munichdeveloper/kitly/issues/new?labels=bug)
- [Request a Feature](https://github.com/munichdeveloper/kitly/issues/new?labels=enhancement)

## 💬 Support

- 📖 [Documentation](docs/)
- 💡 [GitHub Discussions](https://github.com/munichdeveloper/kitly/discussions)
- 🐛 [Issue Tracker](https://github.com/munichdeveloper/kitly/issues)

## 👏 Acknowledgments

Built with:
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Next.js](https://nextjs.org/)
- [Testcontainers](https://www.testcontainers.org/)
- [Flyway](https://flywaydb.org/)
- [PostgreSQL](https://www.postgresql.org/)

---

**Built with ❤️ for modern SaaS applications**

*Ready for production, built for scale*