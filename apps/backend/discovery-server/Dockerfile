FROM eclipse-temurin:25-jdk
WORKDIR /app

# 1. Copy Maven wrapper and POM configuration
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings and set execution permissions for wrapper
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# 2. Pre-fetch and cache Maven dependencies in a dedicated layer
RUN ./mvnw dependency:resolve -B

# 3. Copy application source tree
COPY src ./src

EXPOSE 9737

CMD ["./mvnw", "spring-boot:run"]
