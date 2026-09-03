# Stage 1: Build frontend
FROM --platform=linux/amd64 node:20-alpine AS frontend-build
WORKDIR /frontend
COPY frontend_vue/package*.json ./
RUN npm ci
COPY frontend_vue/ ./
COPY pools/ ../pools/
RUN npm run build

# Stage 2: Build backend
FROM --platform=linux/amd64 maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /backend
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -B
COPY backend/src ./src
RUN mvn package -DskipTests -B

# Stage 3: Runtime
FROM --platform=linux/amd64 eclipse-temurin:17-jre-alpine
RUN apk add --no-cache nginx bash
WORKDIR /app
COPY --from=backend-build /backend/target/backend-*.jar /app/backend.jar
COPY --from=frontend-build /frontend/dist /usr/share/nginx/html
COPY backend/cache /app/cache
COPY nginx.conf /etc/nginx/nginx.conf
COPY start.sh /app/start.sh
EXPOSE 80
CMD ["/app/start.sh"]
