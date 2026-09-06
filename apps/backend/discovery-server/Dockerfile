FROM eclipse-temurin:25-jdk

# Install curl for container healthchecks
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

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

# 4. Pre-compile application and cache build plugins during image build
RUN ./mvnw package -DskipTests -B

EXPOSE 9737

CMD ["./mvnw", "spring-boot:run"]
