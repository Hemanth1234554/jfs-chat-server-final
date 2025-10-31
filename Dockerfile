# --- STAGE 1: Build the Java App ---
# We use a standard Java 17 image (which has Maven) to build our project
FROM openjdk:17-jdk-slim AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven "recipe" file
COPY pom.xml .

# Copy the rest of our source code (src folder)
COPY src ./src

# Run the Maven command to build our single, runnable JAR file
# This will download all dependencies
RUN ./mvnw package -DskipTests

# --- STAGE 2: Create the Final, Small Image ---
# We use a minimal Java 17 image to *run* the app (smaller = faster)
FROM openjdk:17-jre-slim

# Set the working directory
WORKDIR /app

# Copy the JAR file we built in Stage 1
COPY --from=builder /app/target/JavaChatApp-0.0.1-SNAPSHOT.jar ./app.jar

# Tell Render what port our app will run on
# This MUST match the 8080 in our Java code
EXPOSE 8080

# The final command to start our server when the container runs
CMD ["java", "-jar", "./app.jar"]
```

As you requested, here is the full code in the chat as well:

```dockerfile
# --- STAGE 1: Build the Java App ---
# We use a standard Java 17 image (which has Maven) to build our project
FROM openjdk:17-jdk-slim AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven "recipe" file
COPY pom.xml .

# Copy the rest of our source code (src folder)
COPY src ./src

# Run the Maven command to build our single, runnable JAR file
# This will download all dependencies
RUN ./mvnw package -DskipTests

# --- STAGE 2: Create the Final, Small Image ---
# We use a minimal Java 17 image to *run* the app (smaller = faster)
FROM openjdk:17-jre-slim

# Set the working directory
WORKDIR /app

# Copy the JAR file we built in Stage 1
COPY --from=builder /app/target/JavaChatApp-0.0.1-SNAPSHOT.jar ./app.jar

# Tell Render what port our app will run on
# This MUST match the 8080 in our Java code
EXPOSE 8080

# The final command to start our server when the container runs
CMD ["java", "-jar", "./app.jar"]
