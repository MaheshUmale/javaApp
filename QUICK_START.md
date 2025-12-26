# Quick Start Guide: HF-ATS

Fast-track your setup for the High-Frequency Auction Trading System.

## Prerequisites
- **Java 21+**
- **Maven 3.9+**
- **Node.js 18+**

---

## 🛠️ Build from Source
The project uses a tiered build process to save developer time.

### First Time Setup or When UI Changes:
```bash
# Full clean build with React UI (~3 minutes)
mvn clean install -P ui -DskipTests
```

### Java-Only Changes (Fast Iteration):
```bash
# Incremental build - keeps existing UI (~15 seconds)
mvn install -DskipTests
```

### UI Development (Hot Reload):
```bash
# Terminal 1: Run backend
java -jar ats-dashboard/target/ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar

# Terminal 2: Run Vite dev server (instant updates!)
cd ats-dashboard/frontend
npm run dev
# Opens at http://localhost:5173
```

> **💡 Pro Tip:** Only use `clean` when you need a fresh start. For daily Java development, 
> use `mvn install` (without `clean`) to keep your UI build intact!

---

## 🚀 Simulation Mode (Safe Testing)
Verify strategies using deterministic playback.

### 1. Configure the Application
Create or edit `config.properties` in the root directory.

```properties
# Default run mode (live or simulation)
run.mode=live

# Simulation replay source (sample_data or questdb)
replay.source=questdb

# QuestDB Persistence (required for simulation and replay analysis)
questdb.enabled=true
database.persistence.enabled=true

# Upstox API Credentials (Required for Live Mode)
# This key changes daily, update it here without recompiling the JAR.
upstox.accessToken=YOUR_DAILY_ACCESS_TOKEN
```

### 2. Run the Application
The application defaults to **LIVE** mode. To change the mode, you can pass a command-line argument instead of changing the config file.

#### Default (Live Mode)
```powershell
java -jar ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar
```

#### Simulation Mode (Using QuestDB Data)
```powershell
java -jar ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar simulation
```

> [!TIP]
> **Daily Access Token**: Since the access token is outside the JAR in `config.properties`, you can simply update the file every morning and restart the JAR. No recompilation is needed.

> [!IMPORTANT]
> **High-Fidelity Simulation**: By setting `replay.source=questdb`, the application will pull actual historical ticks from QuestDB for the simulation, providing a 100% accurate replay of past market conditions.

---

## 📈 Live Mode (Real-time)
Connect to Upstox API v3 for live execution.

1. **Config**: `ats-core/src/main/resources/config.properties`
   ```properties
   run.mode=live
   upstox.accessToken=YOUR_ACCESS_TOKEN
   dashboard.enabled=true
   ```
2. **Launch**: Build and run the JAR as shown above.
3. **View**: [http://localhost:7070](http://localhost:7070)

---

## 🛠️ Recent Improvements
- **Auction Profile**: Corrected Y-axis scaling for high-priced underlying (Nifty).
- **Option Chain**: Symmetrical layout around Strike Price for faster reading.
- **Active Trades**: Clearer status messages for trade opportunity generation.
- **Data Flow**: Optimized 1Hz snapshot updates for low-latency dashboard feel.
