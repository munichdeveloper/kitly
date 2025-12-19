# Production Deployment Guide

## Overview

This guide covers deploying Kitly to production using Docker, Kubernetes, or traditional server deployment.

## Prerequisites

- Domain name with DNS access
- SSL certificate (Let's Encrypt recommended)
- PostgreSQL database (managed service recommended)
- Container registry (Docker Hub, ECR, GCR)
- CI/CD pipeline (optional but recommended)

## Deployment Options

### Option 1: Docker Compose (Small Scale)

Best for: Small teams, development/staging environments

#### 1. Prepare Server

```bash
# Install Docker and Docker Compose
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh
sudo apt-get install docker-compose-plugin

# Create application directory
mkdir -p /opt/kitly
cd /opt/kitly
```

#### 2. Create Production docker-compose.yml

```yaml
version: '3.8'

services:
  db:
    image: postgres:15-alpine
    restart: always
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - kitly-network

  backend:
    image: your-registry/kitly-backend:latest
    restart: always
    depends_on:
      - db
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/${DB_NAME}
      - SPRING_DATASOURCE_USERNAME=${DB_USER}
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
      - JWT_SESSION_SECRET=${JWT_SESSION_SECRET}
      - CORS_ALLOWED_ORIGINS=${FRONTEND_URL}
    networks:
      - kitly-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  frontend:
    image: your-registry/kitly-frontend:latest
    restart: always
    depends_on:
      - backend
    environment:
      - NEXT_PUBLIC_API_URL=${BACKEND_URL}
    networks:
      - kitly-network

  nginx:
    image: nginx:alpine
    restart: always
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./ssl:/etc/nginx/ssl:ro
    depends_on:
      - frontend
      - backend
    networks:
      - kitly-network

volumes:
  postgres_data:

networks:
  kitly-network:
    driver: bridge
```

#### 3. Create .env File

```bash
# Database
DB_NAME=kitlydb
DB_USER=kitly
DB_PASSWORD=strong-password-here

# JWT
JWT_SECRET=your-production-jwt-secret-min-256-bits
JWT_SESSION_SECRET=your-production-session-secret

# URLs
FRONTEND_URL=https://your-domain.com
BACKEND_URL=https://api.your-domain.com/api
```

#### 4. Create Nginx Configuration

```nginx
# nginx.conf
http {
    upstream backend {
        server backend:8080;
    }

    upstream frontend {
        server frontend:3000;
    }

    server {
        listen 80;
        server_name your-domain.com api.your-domain.com;
        return 301 https://$server_name$request_uri;
    }

    server {
        listen 443 ssl http2;
        server_name your-domain.com;

        ssl_certificate /etc/nginx/ssl/fullchain.pem;
        ssl_certificate_key /etc/nginx/ssl/privkey.pem;

        location / {
            proxy_pass http://frontend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
    }

    server {
        listen 443 ssl http2;
        server_name api.your-domain.com;

        ssl_certificate /etc/nginx/ssl/fullchain.pem;
        ssl_certificate_key /etc/nginx/ssl/privkey.pem;

        location / {
            proxy_pass http://backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header Authorization $http_authorization;
        }
    }
}
```

#### 5. Deploy

```bash
# Pull images
docker-compose pull

# Start services
docker-compose up -d

# Check logs
docker-compose logs -f

# Check status
docker-compose ps
```

### Option 2: Kubernetes (Production Scale)

Best for: Large scale, high availability, auto-scaling

#### 1. Build and Push Images

```bash
# Backend
cd backend
docker build -t your-registry/kitly-backend:v1.0.0 .
docker push your-registry/kitly-backend:v1.0.0

# Frontend
cd frontend
docker build -t your-registry/kitly-frontend:v1.0.0 .
docker push your-registry/kitly-frontend:v1.0.0
```

#### 2. Create Kubernetes Resources

**backend-deployment.yaml**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kitly-backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: kitly-backend
  template:
    metadata:
      labels:
        app: kitly-backend
    spec:
      containers:
      - name: backend
        image: your-registry/kitly-backend:v1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: kitly-secrets
              key: database-url
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: kitly-secrets
              key: jwt-secret
        livenessProbe:
          httpGet:
            path: /api/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /api/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: kitly-backend
spec:
  selector:
    app: kitly-backend
  ports:
  - port: 80
    targetPort: 8080
  type: ClusterIP
```

**frontend-deployment.yaml**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kitly-frontend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: kitly-frontend
  template:
    metadata:
      labels:
        app: kitly-frontend
    spec:
      containers:
      - name: frontend
        image: your-registry/kitly-frontend:v1.0.0
        ports:
        - containerPort: 3000
        env:
        - name: NEXT_PUBLIC_API_URL
          value: "https://api.your-domain.com/api"
---
apiVersion: v1
kind: Service
metadata:
  name: kitly-frontend
spec:
  selector:
    app: kitly-frontend
  ports:
  - port: 80
    targetPort: 3000
  type: ClusterIP
```

**ingress.yaml**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: kitly-ingress
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - your-domain.com
    - api.your-domain.com
    secretName: kitly-tls
  rules:
  - host: your-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: kitly-frontend
            port:
              number: 80
  - host: api.your-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: kitly-backend
            port:
              number: 80
```

#### 3. Create Secrets

```bash
# Create secrets
kubectl create secret generic kitly-secrets \
  --from-literal=database-url=jdbc:postgresql://your-db-host:5432/kitlydb \
  --from-literal=database-user=kitly \
  --from-literal=database-password=your-password \
  --from-literal=jwt-secret=your-jwt-secret \
  --from-literal=jwt-session-secret=your-session-secret
```

#### 4. Deploy to Kubernetes

```bash
# Apply configurations
kubectl apply -f backend-deployment.yaml
kubectl apply -f frontend-deployment.yaml
kubectl apply -f ingress.yaml

# Check status
kubectl get pods
kubectl get services
kubectl get ingress

# View logs
kubectl logs -f deployment/kitly-backend
kubectl logs -f deployment/kitly-frontend
```

## Database Setup

### Managed Database (Recommended)

Use managed PostgreSQL from:
- AWS RDS
- Google Cloud SQL
- Azure Database for PostgreSQL
- DigitalOcean Managed Databases

Benefits:
- Automated backups
- High availability
- Automatic updates
- Monitoring and alerts

### Self-Hosted Database

```bash
# Create PostgreSQL container
docker run -d \
  --name postgres \
  --restart always \
  -e POSTGRES_DB=kitlydb \
  -e POSTGRES_USER=kitly \
  -e POSTGRES_PASSWORD=your-password \
  -v postgres_data:/var/lib/postgresql/data \
  -p 5432:5432 \
  postgres:15-alpine

# Run migrations
cd backend
mvn flyway:migrate \
  -Dflyway.url=jdbc:postgresql://your-host:5432/kitlydb \
  -Dflyway.user=kitly \
  -Dflyway.password=your-password
```

## Security Checklist

- [ ] Change all default passwords
- [ ] Use strong JWT secrets (min 256 bits)
- [ ] Enable HTTPS/TLS
- [ ] Configure firewall rules
- [ ] Set up database backups
- [ ] Enable rate limiting
- [ ] Configure CORS properly
- [ ] Use environment variables for secrets
- [ ] Enable database connection encryption
- [ ] Set up monitoring and alerts
- [ ] Configure log aggregation
- [ ] Implement security headers
- [ ] Regular security updates

## Monitoring

### Application Monitoring

```yaml
# Add Spring Boot Actuator
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Endpoints:
- `/actuator/health` - Health status
- `/actuator/metrics` - Application metrics
- `/actuator/info` - Application info

### External Monitoring

Recommended tools:
- **Prometheus** + **Grafana** for metrics
- **ELK Stack** for logs
- **Sentry** for error tracking
- **Uptime Robot** for uptime monitoring

## Backup Strategy

### Database Backups

```bash
# Automated daily backup
0 2 * * * pg_dump -h localhost -U kitly kitlydb | gzip > /backups/kitly-$(date +\%Y\%m\%d).sql.gz

# Restore from backup
gunzip < backup.sql.gz | psql -h localhost -U kitly kitlydb
```

### Application Backups

- Container images in registry
- Configuration files in version control
- Environment variables documented
- SSL certificates backed up

## Scaling

### Horizontal Scaling

```bash
# Docker Compose
docker-compose up --scale backend=3

# Kubernetes
kubectl scale deployment kitly-backend --replicas=5
```

### Vertical Scaling

Increase container resources:
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

## Troubleshooting

### Check Application Logs

```bash
# Docker
docker-compose logs -f backend
docker-compose logs -f frontend

# Kubernetes
kubectl logs -f deployment/kitly-backend
kubectl logs -f deployment/kitly-frontend
```

### Check Health Status

```bash
curl https://api.your-domain.com/api/health
```

### Database Connection Issues

```bash
# Test connection
psql -h your-db-host -U kitly -d kitlydb

# Check connection pool
# Add to application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
```

## Rollback Strategy

### Docker Compose

```bash
# Tag previous version
docker tag your-registry/kitly-backend:latest your-registry/kitly-backend:rollback

# Update docker-compose.yml to previous version
# Restart
docker-compose up -d
```

### Kubernetes

```bash
# Rollback to previous version
kubectl rollout undo deployment/kitly-backend

# Rollback to specific revision
kubectl rollout undo deployment/kitly-backend --to-revision=2

# Check rollout history
kubectl rollout history deployment/kitly-backend
```

## Cost Optimization

- Use managed services for database
- Implement auto-scaling
- Use CDN for static assets
- Enable compression
- Optimize container images
- Use spot/preemptible instances for non-critical workloads

## Support

For production issues:
1. Check application logs
2. Review monitoring dashboards
3. Check database performance
4. Review recent deployments
5. Contact support team
