# --- STAGE 1: Build the Java App ---
# --- THIS LINE IS FIXED (AGAIN) ---
# Using the official Eclipse Temurin image for Java 17 + Maven
FROM maven:3-eclipse-temurin-17 AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven "recipe" file
COPY pom.xml .

# Copy the rest of our source code (src folder)
COPY src ./src

# We now use the 'mvn' command directly
RUN mvn package -DskipTests

# --- STAGE 2: Create the Final, Small Image ---
# This image name was correct from our last fix
FROM openjdk:17-slim

# Set the working directory
WORKDIR /app

# Copy the JAR file we built in Stage 1
COPY --from=builder /app/target/JavaChatApp-0.0.1-SNAPSHOT.jar ./app.jar

# Tell Render what port our app will run on
EXPOSE 8080

# The final command to start our server when the container runs
CMD ["java", "-jar", "./app.jar"]

### Your Next Action (Very Important):

1.  **Save** the clean `Dockerfile` in Eclipse.
2.  Go to **GitHub Desktop**.
3.  You will see **1 changed file** (`Dockerfile`).
4.  In the "Summary" box, type: `Fix Docker builder image`
5.  Click **"Commit to main"**.
6.  Click **"Push origin"**.

