# AI Server

Welcome to the AI Server project for ai-server.honlnk.top.

## Overview
This is a Spring Boot application serving as an AI backend server.

## Prerequisites
- Java 17 or higher
- Maven 3.6 or higher

## Getting Started

### Running locally
```bash
mvn spring-boot:run
```

### Packaging the application
```bash
mvn clean package
```

### Running the packaged application
```bash
java -jar target/ai-server-0.0.1-SNAPSHOT.jar
```

## Default Endpoints
- `GET /` - Home endpoint
- `GET /health` - Health check endpoint

## Configuration
The application configuration can be found in `src/main/resources/application.properties`.

## Project Structure
Based on the domain ai-server.honlnk.top, the package structure is organized as:
- `com.honlnk.ai.server.ai_server` - Main application package
- `com.honlnk.ai.server.ai_server.controller` - REST controllers