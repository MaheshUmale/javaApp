# 🎮 Quick Reference: Running Simulation Mode

## ✅ **FIXED Configuration** (No QuestDB Needed)

Your `config.properties` is now set to:
```properties
questdb.enabled=false       # ← No QuestDB needed for simulation
replay.source=sample_data   # ← Use synthetic data file
```

---

## 🚀 **How to Run Simulation**

```bash
java -jar ats-dashboard\target\ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar simulation
```

**What happens:**
1. ✅ Loads `data/simulation_data.json.gz` (~1000 synthetic ticks)
2. ✅ Replays at 100ms per tick (configurable)
3. ✅ Dashboard runs on http://localhost:7070
4. ✅ No QuestDB dependency
5. ✅ Fast testing environment

---

## 📊 **What is "Simulation Mode"?**

### **Simulation = Replay of Synthetic/Historical Data**

**NOT live trading:**
- ❌ Not connected to Upstox WebSocket
- ✅ Replaying pre-recorded data
- ✅ Deterministic (same data every time)
- ✅ Safe for testing

---

## 🎯 **Three Ways to Run the System**

### **1. Live Trading (Real Market)**
```properties
run.mode=live
questdb.enabled=true  # Persist real data
```
```bash
.\OptimizedStartup.ps1
```
**Data Source:** Upstox WebSocket → Real market ticks

---

### **2. Simulation with Sample Data (Fast Testing)**
```properties
run.mode=simulation
questdb.enabled=false  # No persistence needed
replay.source=sample_data
```
```bash
java -jar ats-dashboard\target\ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar simulation
```
**Data Source:** `data/simulation_data.json.gz` → ~1000 synthetic ticks

---

### **3. Simulation with QuestDB Replay (Full-Day Replay)**
```properties
run.mode=simulation
questdb.enabled=false  # Don't write during replay
replay.source=questdb
```
```bash
# QuestDB must be running to READ from
java -jar ats-dashboard\target\ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar simulation
```
**Data Source:** QuestDB `ticks` table → Real captured market data

---

## ❓ **Why "Partial Data"?**

The sample data file contains **~1000 ticks** for:
- **Nifty Index**
- **Few Option instruments**

This is **intentional** for fast testing. It's not a full market day.

**For full-day replay:**
1. Capture live data to QuestDB first (run live mode for a full day)
2. Then replay from QuestDB using `replay.source=questdb`

---

## 🔧 **Configuration Quick Reference**

| Scenario | questdb.enabled | replay.source | QuestDB Running? |
|----------|----------------|---------------|------------------|
| **Live Trading** | `true` | N/A | ✅ Yes |
| **Quick Simulation** | `false` | `sample_data` | ❌ No |
| **Full-Day Replay** | `false` | `questdb` | ✅ Yes (read only) |

---

## 🐛 **Troubleshooting**

### **"Could not connect to host [localhost]"**
```
io.questdb.cutlass.line.LineSenderException: [10061] could not connect
```

**Solution:** Set `questdb.enabled=false` in config.properties

---

### **"Simulation finishes too quickly"**
Sample data only has ~1000 ticks.

**Solution:** 
- Slow down: Set `simulation.event.delay.ms=1000` (1 tick per second)
- Or use QuestDB replay with real captured data

---

### **"Dashboard shows no data"**
After simulation finishes, dashboard keeps running.

**Expected:** Dashboard shows last state from simulation  
**Limitation:** Sample data is limited, not all widgets may have data

---

## 📝 **Summary**

**Current Setup (Fixed):**
- ✅ `questdb.enabled=false` - No QuestDB needed
- ✅ `replay.source=sample_data` - Uses synthetic data
- ✅ Can run simulation without QuestDB server

**To Run:**
```bash
java -jar ats-dashboard\target\ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar simulation
```

**To View:**
```
http://localhost:7070
```

---

**Simulation is ready to run! No QuestDB required! 🎉**
