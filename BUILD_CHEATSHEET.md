# ⚡ Maven Build Cheat Sheet

## The Golden Rule: **Avoid `clean` for Daily Work!**

---

## 📋 Command Reference

### 🆕 First Time / UI Changed
```bash
mvn clean install -P ui -DskipTests
```
**Time:** ~3 minutes  
**Rebuilds:** Everything (Java + React UI)

---

### ⚡ Java Changed (99% of the time)
```bash
mvn install -DskipTests
```
**Time:** ~15 seconds  
**Rebuilds:** Only Java (keeps UI!)

---

### 🎨 UI Development
```bash
# Backend (Terminal 1)
java -jar ats-dashboard/target/ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar

# Frontend Dev Server (Terminal 2)
cd ats-dashboard/frontend
npm run dev
```
**Time:** Instant hot reload  
**URL:** http://localhost:5173

---

### 🔧 Something Broken / Git Pull
```bash
mvn clean install -P ui -DskipTests
```
**Time:** ~3 minutes  
**Rebuilds:** Everything fresh

---

## 🎯 Why This Works

| Command | Deletes target/ | Builds UI | Time |
|---------|----------------|-----------|------|
| `mvn clean install -P ui` | ✅ Yes | ✅ Yes | 3 min |
| `mvn install` | ❌ No | ❌ No (keeps existing) | 15 sec |
| `mvn clean install` | ✅ Yes | ❌ No (UI lost!) | 17 sec |

---

## ⚠️ Common Mistakes

### ❌ **WRONG:**
```bash
mvn clean install    # Deletes UI! Now you have old static HTML!
```

### ✅ **CORRECT:**
```bash
mvn install          # Keeps UI intact, fast Java rebuild!
```

---

## 💾 Typical Development Day

```bash
# Morning (once)
mvn clean install -P ui -DskipTests

# Rest of the day (repeat as needed)
mvn install -DskipTests
.\OptimizedStartup.ps1
# ... make Java changes ...
mvn install -DskipTests
.\OptimizedStartup.ps1
# ... make more changes ...
```

**Total build time:** 3 min + (N × 15 sec) instead of (N × 3 min)!

---

## 📊 Time Savings

**Old way (always using clean):**
- 10 builds/day × 3 min = **30 minutes wasted**

**New way (incremental builds):**
- 1 clean build + 9 incremental builds
- 3 min + (9 × 15 sec) = **5.25 minutes total**
- **Savings: 25 minutes per day!**

---

## 🚀 Quick Copy-Paste

```bash
# Daily Java development (copy this!)
mvn install -DskipTests && .\OptimizedStartup.ps1
```

---

**Remember: `clean` is the enemy of productivity! 😄**
