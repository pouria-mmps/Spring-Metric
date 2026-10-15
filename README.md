# Spring Metric

Minimal REST API using Spring Boot 4.1.1 and Java 25.

## Prerequisites

Install JDK 25 and set `JAVA_HOME` to its installation directory. Maven is downloaded
automatically by the included Maven wrapper on first use. Internet access is required
to download Maven and dependencies.

For the JDK already installed on this Windows machine, use PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Users\Green House\.jdks\corretto-25.0.4.1'
```

Configure IntelliJ's project SDK and Maven runner JRE to use JDK 25 as well.

## Build and test

```powershell
.\mvnw.cmd verify
```

On Linux or macOS, use `./mvnw` instead of `.\mvnw.cmd`.

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

The application listens on port 8080 by default.

## REST API

`GET http://localhost:8080/api/hello` returns HTTP 200 with JSON:

```json
{"message":"Hello, world!"}
```

Try it in PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/api/hello
```
