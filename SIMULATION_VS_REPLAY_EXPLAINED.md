# 🎥 Simulation vs Replay - Understanding the Difference

**Question:** Why is simulation creating partial data feeds? Where does simulation get data? Is simulation different from replay?

**Answer:** Yes, **simulation** and **replay** have different meanings in this system, though they're often used interchangeably.

---

## 📖 Definitions

### **Simulation Mode** = The Application Mode
```java
// Main.java line 18
String runMode = ConfigLoader.getProperty("run.mode", "live");

// Two modes:
// 1. "live" - Connect to Upstox WebSocket for real market data
// 2. "simulation" - Use replayer for historical/synthetic data
```

**Simulation mode is the APPLICATION MODE that uses a replayer for data.**

### **Replay Source** = Where the Data Comes From
```java
// Main.java line 184
String replaySource = ConfigLoader.getProperty("replay.source", "questdb");

// Two sources:
// 1. "sample_data" - Pre-generated synthetic data from files
// 2. "questdb" - Real historical data from QuestDB database
```

**Replay source determines WHICH replayer to use.**

---

## 🔀 The Two Data Paths

### Path 1: **Live Mode** (Real Trading)
```
User Request
    ↓
Main.java (run.mode=live)
    ↓
UpstoxMarketDataStreamer
    ↓
WebSocket → Upstox API → Real Market Ticks
    ↓
Disruptor RingBuffer → Strategy → Dashboard
```

**Data Source:** Live market feed from Upstox  
**Purpose:** Real trading / Real-time monitoring

---

### Path 2: **Simulation Mode with Sample Data**
```
User Request
    ↓
Main.java (run.mode=simulation, replay.source=sample_data)
    ↓
MySampleDataReplayer
    ↓
Loads: data/simulation_data.json.gz
    ↓
Disruptor RingBuffer → Strategy → Dashboard
```

**Data Source:** `d:\Java_CompleteProject\Java_CompleteSystem\data\simulation_data.json.gz`  
**Purpose:** Quick testing with synthetic data  
**Status:** ✅ **This is what you're currently using!**

---

### Path 3: **Simulation Mode with QuestDB Replay**
```
User Request
    ↓
Main.java (run.mode=simulation, replay.source=questdb)
    ↓
QuestDBReplayer
    ↓
Connects to: jdbc:postgresql://localhost:8812/qdb
Queries: SELECT * FROM ticks ORDER BY ltt
    ↓
Disruptor RingBuffer → Strategy → Dashboard
```

**Data Source:** QuestDB database table `ticks`  
**Purpose:** High-fidelity replay of captured market data  
**Status:** ⚠️ **Requires QuestDB with data!**

---

## 🗂️ Where Simulation Data is Located

### Current Configuration:
```properties
# config.properties
replay.source=sample_data
questdb.enabled=true  # ← For WRITING new data
```

### File Locations:

#### **Sample Data File:**
```
Location: d:\Java_CompleteProject\Java_CompleteSystem\data\simulation_data.json.gz
Size: 70,862 bytes (~70 KB)
Format: GZIP-compressed JSON
Content: Synthetic market ticks
```

**Code path** (MySampleDataReplayer.java line 28-29, 38-41):
```java
private final List<String> dataFiles = Arrays.asList("simulation_data.json.gz");

// Line 41: Looks for file at
processFile(dataDirectory + "/" + fileName);
// = "data/simulation_data.json.gz"

// Line 49: First tries classpath, then file system
InputStream resourceIs = getClass().getClassLoader()
    .getResourceAsStream(filePath);
```

#### **QuestDB Tables** (if using questdb replayer):
```
Location: QuestDB database (localhost:8812)
Table: ticks
Schema:
  - symbol (STRING)
  - ltp (DOUBLE)
  - ltq (LONG)
  - ltt (TIMESTAMP)
  - cp, tbq, tsq, vtt, oi, iv, atp, best_bid, best_ask
```

**Code path** (QuestDBReplayer.java line 38):
```java
String query = "SELECT * FROM ticks ORDER BY ltt";
```

---

## ⚠️ Why "Partial Data Feeds"?

### The Issue:

When you run:
```bash
java -jar ./ats-dashboard/target/ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar simulation
```

With:
```properties
questdb.enabled=true
replay.source=sample_data
```

**What happens:**
1. ✅ System starts in **simulation mode**
2. ✅ Uses `MySampleDataReplayer`
3. ✅ Loads `data/simulation_data.json.gz` 
4. ⚠️ **Tries to connect to QuestDB for WRITING** (because `questdb.enabled=true`)
5. ❌ **QuestDB not running** → Connection error → System exits

**The error you saw:**
```
io.questdb.cutlass.line.LineSenderException: [10061] could not connect to 
host [host=localhost]
```

**Root cause:** `questdb.enabled=true` makes the system try to:
- Create `QuestDBWriter` to WRITE replay data to QuestDB
- Connect to `localhost:9009` (QuestDB ILP port)
- **Fails because QuestDB server not running**

---

## ✅ Solution: Two Options

### **Option 1: Disable QuestDB for Sample Data Simulation**

**Edit `config.properties`:**
```properties
# Application Run Mode
run.mode=simulation  # ← Can set here or use command line

# QuestDB Integration - DISABLE for sample data simulation
questdb.enabled=false  # ← Change from true to false

# Replay source
replay.source=sample_data

# Simulation speed
simulation.event.delay.ms=100
```

**Run:**
```bash
java -jar ats-dashboard\target\ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar simulation
```

**Result:**
- ✅ Loads sample data
- ✅ No QuestDB dependency
- ✅ Fast simulation
- ❌ No data persistence (not needed for testing)

---

### **Option 2: Keep QuestDB Enabled (Full Persistence)**

**Before starting simulation:**
```bash
# 1. Start QuestDB server first
# (however you start QuestDB - separate window/service)

# 2. Verify QuestDB is running
netstat -an | findstr "9009 8812"

# Should see:
#   TCP    0.0.0.0:9009    (ILP port for writing)
#   TCP    0.0.0.0:8812    (PostgreSQL wire protocol for queries)

# 3. Run simulation
java -jar ats-dashboard\target\ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar simulation
```

**Result:**
- ✅ Loads sample data
- ✅ Writes to QuestDB (persists simulated data)
- ✅ Can query data later
- ✅ Can replay from QuestDB next time

---

## 🎯 Recommended Workflow

### For Quick Testing (No Persistence):
```properties
# config.properties
questdb.enabled=false
replay.source=sample_data
```

```bash
# Just run - no QuestDB needed
java -jar ats-dashboard\target\ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar simulation
```

---

### For Full-Day Replay (With Persistence):

**Step 1: Capture Live Data**
```properties
# config.properties
run.mode=live
questdb.enabled=true  # ← Capture real data
```

```bash
# Start QuestDB first
# Then start trading system
.\OptimizedStartup.ps1

# Let it run during market hours
# All ticks written to QuestDB 'raw_market_feed' table
```

**Step 2: Replay Captured Data**
```properties
# config.properties
run.mode=simulation
questdb.enabled=false  # ← Don't write during replay
replay.source=questdb  # ← Read from QuestDB
```

```bash
# QuestDB must be running (to read from)
java -jar ats-dashboard\target\ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar simulation
```

---

## 📊 Data Flow Comparison

### **Sample Data (Generated):**
```
simulation_data.json.gz (70 KB)
    ↓
Contains: ~1000 synthetic ticks
Instruments: Nifty Index, Options
Quality: Synthetic/Demo
Speed: Fast (10-100ms per tick)
```

### **QuestDB Replay (Real):**
```
QuestDB 'ticks' table
    ↓
Contains: Could be millions of real ticks
Instruments: All subscribed instruments
Quality: 100% accurate real market data
Speed: Configurable (1-100ms per tick)
```

---

## 🔍 Debugging Simulation Issues

### Check What Data is Being Loaded:

**Look for log messages:**
```
[main] INFO com.trading.hf.MySampleDataReplayer - Starting data replay from classpath directory: data
[main] INFO com.trading.hf.MySampleDataReplayer - Processing file: data/simulation_data.json.gz
```

### Check Data File Exists:

```powershell
Get-Item "d:\Java_CompleteProject\Java_CompleteSystem\data\simulation_data.json.gz"
```

Should show: `70,862 bytes`

### Extract and View Sample Data:

```powershell
# Extract the gzipped JSON
$inFile = "d:\Java_CompleteProject\Java_CompleteSystem\data\simulation_data.json.gz"
$outFile = "d:\Java_CompleteProject\Java_CompleteSystem\data\sample_data_extracted.json"
$input = New-Object System.IO.FileStream $inFile, ([IO.FileMode]::Open)
$output = New-Object System.IO.FileStream $outFile, ([IO.FileMode]::Create)
$gzipStream = New-Object System.IO.Compression.GzipStream $input, ([IO.Compression.CompressionMode]::Decompress)
$gzipStream.CopyTo($output)
$gzipStream.Close()
$output.Close()
$input.Close()

# View the JSON
Get-Content $outFile | ConvertFrom-Json | Select-Object -First 5
```

---

## 📝 Summary

| Aspect | Sample Data Simulation | QuestDB Replay |
|--------|----------------------|----------------|
| **Mode** | `simulation` | `simulation` |
| **Source** | `replay.source=sample_data` | `replay.source=questdb` |
| **Data Location** | `data/simulation_data.json.gz` | QuestDB `ticks` table |
| **QuestDB Needed** | ❌ No | ✅ Yes |
| **Data Quality** | Synthetic/Demo | 100% Real |
| **Setup Time** | Instant | Requires capture session |
| **Purpose** | Quick testing | Full-day replay |

---

## 🚀 Quick Fix For You

**Current issue:** `questdb.enabled=true` but QuestDB not running

**Fix:**
```bash
# Edit config.properties
# Change: questdb.enabled=true
# To: questdb.enabled=false

# Then run:
java -jar ats-dashboard\target\ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar simulation
```

**OR:**

```bash
# Start QuestDB first, then run simulation
```

---

**Your "partial data" issue is because the sample data file only contains ~1000 synthetic ticks, not a full day of data. This is intentional for fast testing. For full-day replay, use QuestDB with captured live data!**
