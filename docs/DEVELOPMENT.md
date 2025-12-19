# Development Setup Guide

## Prerequisites

### Required Software
- **Java 17** or higher ([Download](https://adoptium.net/))
- **Maven 3.6+** ([Download](https://maven.apache.org/download.cgi))
- **Node.js 18+** and npm ([Download](https://nodejs.org/))
- **PostgreSQL 15** ([Download](https://www.postgresql.org/download/))
- **Docker** (optional, for containerized development) ([Download](https://www.docker.com/get-started))
- **Git** ([Download](https://git-scm.com/downloads))

### Recommended Tools
- **IntelliJ IDEA** or **VS Code** for backend development
- **VS Code** with React/TypeScript extensions for frontend
- **Postman** or **Insomnia** for API testing
- **pgAdmin** or **DBeaver** for database management

## Quick Start (5 minutes)

### Option 1: Docker Compose (Recommended)

```bash
# Clone the repository
git clone https://github.com/munichdeveloper/kitly.git
cd kitly

# Start all services
docker-compose up

# Access the application
# Frontend: http://localhost:3000
# Backend API: http://localhost:8080/api
# PostgreSQL: localhost:5432
```

### Option 2: Local Development

#### 1. Setup Database

```bash
# Create database
createdb kitlydb

# Or using psql
psql -U postgres
CREATE DATABASE kitlydb;
\q
```

#### 2. Start Backend

```bash
cd backend

# Build and run with Maven
mvn clean install
mvn spring-boot:run

# Backend will be available at http://localhost:8080/api
```

#### 3. Start Frontend

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev

# Frontend will be available at http://localhost:3000
```

## Detailed Setup

### Backend Configuration

#### Environment Variables

Create `.env` file in `backend/` directory:

```bash
# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/kitlydb
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# JWT Configuration
JWT_SECRET=your-secret-key-change-in-production
JWT_EXPIRATION=86400000
JWT_SESSION_SECRET=your-session-secret-key
JWT_SESSION_EXPIRATION=900000

# CORS Configuration
CORS_ALLOWED_ORIGINS=http://localhost:3000

# Application Profile
SPRING_PROFILES_ACTIVE=dev
```

#### Application Profiles

**Development (`application.yml`)**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/kitlydb
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update
  flyway:
    enabled: true
    baseline-on-migrate: true
```

**Test (`application-test.yml`)**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true
```

#### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=TenantServiceTest

# Run integration tests
mvn test -Dtest=*IntegrationTest

# Run with coverage
mvn test jacoco:report
# Report will be at target/site/jacoco/index.html
```

#### Database Migrations

Flyway migrations are in `backend/src/main/resources/db/migration/`:

```bash
# Clean database (CAUTION: deletes all data)
mvn flyway:clean

# Run migrations
mvn flyway:migrate

# Get migration info
mvn flyway:info

# Validate migrations
mvn flyway:validate
```

#### Building

```bash
# Build JAR
mvn clean package

# Skip tests
mvn clean package -DskipTests

# Run JAR
java -jar target/kitly-backend-1.0.0.jar
```

### Frontend Configuration

#### Environment Variables

Create `.env.local` file in `frontend/` directory:

```bash
# API Configuration
NEXT_PUBLIC_API_URL=http://localhost:8080/api

# Optional: OIDC Configuration
# NEXT_PUBLIC_OIDC_ISSUER=https://your-oidc-provider.com
# NEXT_PUBLIC_OIDC_CLIENT_ID=your-client-id
```

#### Running Development Server

```bash
# Start dev server with hot reload
npm run dev

# Start on different port
npm run dev -- -p 3001

# Build for production
npm run build

# Start production server
npm start

# Run type checking
npm run type-check

# Run linter
npm run lint
```

#### Frontend Structure

```
frontend/
├── app/
│   ├── auth/
│   │   ├── login/         # Login page
│   │   └── signup/        # Signup page
│   ├── dashboard/         # Protected dashboard
│   ├── layout.tsx         # Root layout
│   └── page.tsx           # Home page
├── components/            # Reusable UI components
├── lib/
│   ├── api.ts            # API client
│   └── auth-context.tsx  # Authentication context
├── public/               # Static assets
└── styles/               # Global styles
```

## Development Workflow

### 1. Feature Development

```bash
# Create feature branch
git checkout -b feature/your-feature-name

# Make changes
# ...

# Run tests
mvn test                  # Backend
npm test                  # Frontend

# Commit changes
git add .
git commit -m "feat: add your feature"

# Push and create PR
git push origin feature/your-feature-name
```

### 2. Database Changes

```bash
# Create new migration
# File: backend/src/main/resources/db/migration/V17__description.sql

# Test migration
mvn flyway:migrate

# If needed, rollback (clean + migrate)
mvn flyway:clean
mvn flyway:migrate
```

### 3. Testing Strategy

**Unit Tests**
- Test individual service methods
- Mock dependencies
- Fast execution

**Integration Tests**
- Test complete flows
- Use Testcontainers for PostgreSQL
- Validate database interactions

**API Tests**
- Test REST endpoints
- Validate request/response
- Check authentication/authorization

## Common Development Tasks

### Adding a New Entity

1. **Create Entity Class**
```java
@Entity
@Table(name = "your_entities")
public class YourEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    // fields, getters, setters
}
```

2. **Create Repository**
```java
public interface YourRepository extends JpaRepository<YourEntity, UUID> {
    // custom queries
}
```

3. **Create Service**
```java
@Service
public class YourService {
    private final YourRepository repository;
    // business logic
}
```

4. **Create Controller**
```java
@RestController
@RequestMapping("/api/your-entities")
public class YourController {
    private final YourService service;
    // endpoints
}
```

5. **Create Migration**
```sql
-- V17__create_your_entities.sql
CREATE TABLE your_entities (
    id UUID PRIMARY KEY,
    -- columns
);
```

### Adding a New API Endpoint

1. **Add Controller Method**
```java
@GetMapping("/{id}")
public ResponseEntity<YourDTO> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(service.getById(id));
}
```

2. **Create DTO**
```java
@Data
@Builder
public class YourDTO {
    private UUID id;
    // fields
}
```

3. **Add Service Method**
```java
public YourDTO getById(UUID id) {
    YourEntity entity = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Not found"));
    return mapToDTO(entity);
}
```

4. **Write Tests**
```java
@Test
void testGetById() {
    // arrange
    // act
    // assert
}
```

## Troubleshooting

### Backend Issues

**Problem:** Port 8080 already in use
```bash
# Find process using port
lsof -i :8080
# Kill process
kill -9 <PID>
```

**Problem:** Database connection fails
```bash
# Check PostgreSQL is running
pg_isready -h localhost -p 5432

# Check credentials in application.yml
# Verify database exists
psql -U postgres -l
```

**Problem:** Flyway migration fails
```bash
# Check migration order
mvn flyway:info

# Clean and re-migrate (CAUTION: deletes data)
mvn flyway:clean
mvn flyway:migrate
```

### Frontend Issues

**Problem:** Module not found
```bash
# Clear cache and reinstall
rm -rf node_modules package-lock.json
npm install
```

**Problem:** API calls fail (CORS)
```bash
# Check CORS configuration in backend
# Verify API URL in .env.local
# Check browser console for errors
```

**Problem:** Build fails
```bash
# Type check
npm run type-check

# Clear Next.js cache
rm -rf .next
npm run build
```

## IDE Setup

### IntelliJ IDEA

1. **Import Project**: File → Open → Select `backend/pom.xml`
2. **Enable Annotation Processing**: Settings → Build → Compiler → Annotation Processors
3. **Install Plugins**: Lombok, Spring Boot
4. **Configure Run Configuration**:
   - Main class: `com.kitly.saas.KitlyApplication`
   - VM options: `-Dspring.profiles.active=dev`

### VS Code

1. **Install Extensions**:
   - Java Extension Pack
   - Spring Boot Extension Pack
   - ES7+ React/Redux/React-Native snippets
   - Tailwind CSS IntelliSense

2. **Configure Settings** (`.vscode/settings.json`):
```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "editor.formatOnSave": true
}
```

## Performance Tips

### Backend
- Use connection pooling (HikariCP)
- Enable query caching
- Use projection for large result sets
- Profile with JProfiler or VisualVM

### Frontend
- Use Next.js Image component
- Implement code splitting
- Enable React strict mode
- Use React Developer Tools

## Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Next.js Documentation](https://nextjs.org/docs)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Docker Documentation](https://docs.docker.com/)

## Getting Help

- Check existing issues on GitHub
- Review API documentation in `/docs/API.md`
- Review architecture in `/docs/ARCHITECTURE.md`
- Join community discussions

## Next Steps

After setup:
1. Explore the API with Postman
2. Review existing tests for examples
3. Read the architecture documentation
4. Start with small feature additions
