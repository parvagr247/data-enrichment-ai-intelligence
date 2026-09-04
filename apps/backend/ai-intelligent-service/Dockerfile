FROM eclipse-temurin:25-jdk
WORKDIR /app

# Copy Maven wrapper and POM configuration
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings and set execution permissions for wrapper
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Copy application source tree
COPY src ./src

EXPOSE 9742

CMD ["./mvnw", "spring-boot:run"]
