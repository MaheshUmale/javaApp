# 🚀 High-Frequency ATS: Professional Startup & Resource Guide

This guide ensures the application runs with optimal performance while managing CPU and Memory resources effectively.

## 1. Memory Management (The JVM Heap)
Processing 250+ tick-level feeds requires memory for the Disruptor ring buffers and in-memory caches.

### Recommended JVM Flags
*   **-Xms2g -Xmx4g**: Sets the initial and maximum heap size to 2GB and 4GB respectively.
*   **-XX:+UseZGC**: (Java 21+) Use the Z Garbage Collector for consistent low-latency (sub-millisecond pausestimes).

## 2. CPU Management (Wait Strategies)
The LMAX Disruptor can be aggressive with CPU to achieve low latency. You can control this in `config.properties`.

### `config.properties` Settings
```properties
# Options: blocking, sleeping, yielding, busyspin
# - blocking: Lowest CPU (ideal for local laptops)
# - sleeping: Balanced (recommended default)
# - yielding: High CPU (use on dedicated servers)
# - busyspin: Extreme (100% core usage, lowest latency)
disruptor.wait.strategy=sleeping
```

## 3. Building the Project (Optimized)
The project is optimized for fast backend development.

### Backend Only (Fast Build)
Skip the expensive frontend build if you only changed Java code:
```powershell
mvn clean install -DskipTests
```
*Time: ~45 seconds*

### Full Build (Backend + UI)
Use this if you have modified any files in `ats-dashboard/frontend`:
```powershell
mvn clean install -DskipTests -Pui
```
*Time: ~3 minutes*

## 4. Quick Launch (Optimized)

### Windows (PowerShell)
Copy and save this as `start-ats.ps1` in the root directory:

```powershell
# Optimized Startup Script
$JVM_OPTS = @("-Xms2g", "-Xmx4g", "-XX:+UseZGC")
$JAR="ats-dashboard/target/ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar"

if (Test-Path $JAR) {
    Write-Host "🚀 Launching ATS with Optimized Settings..." -ForegroundColor Green
    java @JVM_OPTS -jar $JAR
} else {
    Write-Host "❌ JAR not found. Run 'mvn clean install' first." -ForegroundColor Red
}
```

### Manual Command
```powershell
java -Xms2g -Xmx4g -XX:+UseZGC -jar ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## 4. Resource Troubleshooting
*   **90% CPU Usage?** Change `disruptor.wait.strategy=blocking` in `config.properties`.
*   **OutOfMemoryError?** Increase `-Xmx` (e.g., `-Xmx8g`) if you are subscribing to thousands of instruments.
*   **Slow Dashboad?** Ensure you are using a modern browser (Chrome/Edge) and consistent network for the WebSocket connection.
