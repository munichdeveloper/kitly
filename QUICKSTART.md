# Quick Start Guide

Get your Kitly SaaS platform running in minutes!

## Prerequisites

- Java 17+
- Maven 3.6+
- Node.js 18+
- npm

## Quick Start (5 minutes)

### 1. Start the Backend

```bash
cd backend
mvn spring-boot:run
```

The backend will be available at `http://localhost:8080`

### 2. Start the Frontend

In a new terminal:

```bash
cd frontend
npm install
npm run dev
```

The frontend will be available at `http://localhost:3000`

### 3. Access the Application

1. Open your browser to `http://localhost:3000`
2. Click "Sign Up" to create a new account
3. Fill in your details (username, email, password)
4. You'll be automatically logged in and redirected to your dashboard

## API Endpoints

### Authentication
- **POST** `/api/auth/signup` - Register a new user
- **POST** `/api/auth/login` - Login

### Users
- **GET** `/api/users/me` - Get current user (requires authentication)
- **GET** `/api/users` - Get all users (Admin only)

### Health
- **GET** `/api/health` - Health check

## Test the API with curl

### Create a user:
```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "email": "john@example.com",
    "password": "password123",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

### Login:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "password": "password123"
  }'
```

This will return a JWT token. Use it for authenticated requests:

```bash
curl -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  http://localhost:8080/api/users/me
```

## Using Docker Compose

```bash
docker-compose up
```

This will start both backend and frontend services.

## Features Included

- ✅ User registration and authentication
- ✅ JWT-based security
- ✅ Role-based access control
- ✅ User dashboard
- ✅ Profile management
- ✅ Responsive UI
- ✅ H2 in-memory database (for dev)

## What's Next?

1. **Configure Database**: Switch from H2 to PostgreSQL or MySQL for production
2. **Add Email**: Integrate email verification
3. **Enhance Security**: Add rate limiting, password strength requirements
4. **Add Features**: Build out your SaaS features on this foundation!

## Troubleshooting

### Backend won't start
- Ensure Java 17+ is installed: `java -version`
- Check if port 8080 is available

### Frontend won't start
- Ensure Node.js 18+ is installed: `node -v`
- Run `npm install` again if needed
- Check if port 3000 is available

### Can't login
- Ensure backend is running
- Check browser console for errors
- Verify API URL in `frontend/.env.local`

## Support

See the main [README.md](README.md) for detailed documentation.
