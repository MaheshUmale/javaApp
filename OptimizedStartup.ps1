# Optimized Startup Script
$JVM_OPTS = @("-Xms2g", "-Xmx4g", "-XX:+UseZGC")
$JAR="ats-dashboard/target/ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar"

if (Test-Path $JAR) {
    Write-Host "🚀 Launching ATS with Optimized Settings..." -ForegroundColor Green
    java @JVM_OPTS -jar $JAR
} else {
    Write-Host "❌ JAR not found. Run 'mvn clean install' first." -ForegroundColor Red
}