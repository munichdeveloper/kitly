# Kitly - SaaS Monorepo Platform

A modern, production-ready monorepo featuring Spring Boot (Maven) backend and Next.js frontend with comprehensive SaaS functionality including authentication, user management, and dashboard.

## 🚀 Features

### Backend (Spring Boot)
- **Authentication & Authorization**: JWT-based authentication with Spring Security
- **User Management**: Complete user registration, login, and profile management
- **Role-Based Access Control**: Admin and User roles with method-level security
- **RESTful API**: Well-structured REST endpoints with proper validation
- **Database**: H2 in-memory database (easily switchable to PostgreSQL/MySQL)
- **API Documentation**: Clean and documented REST API structure

### Frontend (Next.js)
- **Modern UI**: Built with Next.js 16, React 19, and Tailwind CSS
- **Authentication Flow**: Login, signup, and protected routes
- **User Dashboard**: Personalized dashboard with user profile information
- **Responsive Design**: Mobile-first, fully responsive interface
- **TypeScript**: Fully typed for better development experience
- **State Management**: React Context for authentication state

## 📁 Project Structure

```
kitly/
├── backend/                    # Spring Boot backend
│   ├── src/
│   │   └── main/
│   │       ├── java/com/kitly/saas/
│   │       │   ├── config/    # Security, CORS, and initialization config
│   │       │   ├── controller/ # REST API controllers
│   │       │   ├── dto/        # Data Transfer Objects
│   │       │   ├── entity/     # JPA entities (User, Role)
│   │       │   ├── repository/ # Spring Data JPA repositories
│   │       │   ├── security/   # JWT utilities and filters
│   │       │   ├── service/    # Business logic services
│   │       │   └── KitlyApplication.java
│   │       └── resources/
│   │           └── application.yml
│   └── pom.xml
│
├── frontend/                   # Next.js frontend
│   ├── app/
│   │   ├── auth/
│   │   │   ├── login/         # Login page
│   │   │   └── signup/        # Signup page
│   │   ├── dashboard/         # Protected dashboard
│   │   ├── layout.tsx
│   │   └── page.tsx           # Home page
│   ├── components/            # Reusable UI components
│   ├── lib/
│   │   ├── api.ts            # API client
│   │   └── auth-context.tsx  # Authentication context
│   ├── package.json
│   └── .env.local
│
└── README.md
```

## 🛠️ Prerequisites

- **Java 17** or higher
- **Maven 3.6+**
- **Node.js 18+** and npm
- Git

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/munichdeveloper/kitly.git
cd kitly
```

### 2. Start the Backend

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

The backend will start on `http://localhost:8080/api`

### 3. Start the Frontend

In a new terminal:

```bash
cd frontend
npm install
npm run dev
```

The frontend will start on `http://localhost:3000`

## 📝 Usage

### Accessing the Application

1. Open your browser and navigate to `http://localhost:3000`
2. Click "Sign Up" to create a new account
3. Fill in your details and register
4. You'll be automatically logged in and redirected to the dashboard
5. Explore your profile information and available features

### Default Configuration

- **Backend API**: `http://localhost:8080/api`
- **Frontend**: `http://localhost:3000`
- **H2 Console**: `http://localhost:8080/api/h2-console`
  - JDBC URL: `jdbc:h2:mem:kitlydb`
  - Username: `sa`
  - Password: (empty)

## 🔐 API Endpoints

### Authentication

- `POST /api/auth/signup` - Register a new user
- `POST /api/auth/login` - Login and receive JWT token

### Users

- `GET /api/users/me` - Get current user profile (requires authentication)
- `GET /api/users` - Get all users (Admin only)
- `GET /api/users/{id}` - Get user by ID (Admin only)

### Health Check

- `GET /api/health` - Application health check

## 🔧 Configuration

### Backend Configuration

Edit `backend/src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:mem:kitlydb
    # Update for production database

jwt:
  secret: your-secret-key-here  # Change in production!
  expiration: 86400000          # 24 hours

cors:
  allowed-origins: http://localhost:3000
```

### Frontend Configuration

Edit `frontend/.env.local`:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

## 🏗️ Building for Production

### Backend

```bash
cd backend
mvn clean package
java -jar target/kitly-backend-1.0.0.jar
```

### Frontend

```bash
cd frontend
npm run build
npm start
```

## 🧪 Testing

### Backend Tests

```bash
cd backend
mvn test
```

### Frontend Tests

```bash
cd frontend
npm test
```

## 📦 Technologies Used

### Backend
- Spring Boot 4.0.1
- Spring Security
- Spring Data JPA
- JWT (JSON Web Tokens)
- H2 Database
- Lombok
- Maven

### Frontend
- Next.js 16.1.0
- React 19
- TypeScript
- Tailwind CSS
- React Context API

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🌟 Features Roadmap

- [ ] Email verification
- [ ] Password reset functionality
- [ ] OAuth2 integration (Google, GitHub)
- [ ] User profile editing
- [ ] Team/Organization management
- [ ] Payment integration (Stripe)
- [ ] Admin panel
- [ ] API rate limiting
- [ ] Docker support
- [ ] Kubernetes deployment configs

## 📞 Support

For support, please open an issue in the GitHub repository.

## 🎯 Next Steps

After getting the application running, consider:

1. **Security**: Change the JWT secret in production
2. **Database**: Switch from H2 to PostgreSQL or MySQL for production
3. **Environment Variables**: Use proper environment variable management
4. **Monitoring**: Add application monitoring and logging
5. **Testing**: Add comprehensive unit and integration tests
6. **CI/CD**: Set up continuous integration and deployment pipelines
7. **Documentation**: Add API documentation with Swagger/OpenAPI

---

Built with ❤️ using Spring Boot and Next.js