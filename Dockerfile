FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy all files into the Docker container
COPY . .

# Build the application using maven wrapper (fixing Windows line endings just in case)
RUN sed -i 's/\r$//' mvnw
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

# Stage 2: Create the final lightweight image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the built JAR from the first stage
COPY --from=build /app/target/*.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
