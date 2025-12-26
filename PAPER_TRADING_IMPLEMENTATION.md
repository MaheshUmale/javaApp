# 📊 Active Trades Population & Paper Trading Implementation

**Questions:**
1. How does "Active Trades & Strategy" panel get populated?
2. How to convert BUY/SELL buttons to paper trading for strategy performance tracking?

---

## 🔍 Part 1: How Active Trades Gets Populated

### **Current Data Flow:**

```
UpstoxOrderManager.placeOrder()
        ↓
positionManager.addPosition()  ← Adds to ConcurrentHashMap
        ↓
DashboardBridge.sendSnapshot()
        ↓
viewModel.activeTrades = positionManager.getAllPositions()  ← Lines 244-253
        ↓
WebSocket → Frontend
        ↓
ActiveTradesWidget displays trades
```

### **Code Trace:**

#### **1. Position Added (UpstoxOrderManager.java line 93):**
```java
// When a REAL order is placed:
if ("LIMIT".equals(orderType.toUpperCase())) {
    positionManager.addPosition(
        instrumentKey, 
        quantity, 
        side, 
        price,  // entry price
        System.currentTimeMillis()
    );
}
```

#### **2. Position Stored (PositionManager.java line 9-10):**
```java
public void addPosition(String instrumentKey, int quantity, String side, 
                       double entryPrice, long entryTimestamp) {
    positions.put(instrumentKey, 
        new Position(instrumentKey, quantity, side, entryPrice, entryTimestamp));
}
```

#### **3. Dashboard Reads Positions (DashboardBridge.java lines 244-253):**
```java
viewModel.activeTrades = positionManager.getAllPositions().values().stream()
    .map(pos -> {
        TradeViewModel tvm = new TradeViewModel();
        tvm.symbol = getFriendlyName(pos.getInstrumentKey());
        tvm.entry = pos.getEntryPrice();
        tvm.ltp = pos.getEntryPrice();  // TODO: Should update with live price!
        tvm.qty = pos.getQuantity();
        tvm.pnl = 0.0;  // TODO: Should calculate (ltp - entry) * qty
        tvm.reason = pos.getSide();  // "BUY" or "SELL"
        return tvm;
    }).collect(Collectors.toList());
```

#### **4. Frontend Displays (ActiveTradesWidget.jsx lines 37-49):**
```jsx
trades.map((trade) => (
  <tr key={trade.symbol}>
    <td>{trade.symbol}</td>
    <td>{trade.entry.toFixed(1)} / {trade.ltp.toFixed(1)}</td>
    <td>{trade.qty}</td>
    <td>{trade.pnl}</td>
    <td>{trade.reason}</td>
  </tr>
))
```

---

### **Current Status:**

| Feature | Status | Notes |
|---------|--------|-------|
| **Position Storage** | ✅ Working | ConcurrentHashMap |
| **Dashboard Display** | ✅ Working | Shows positions |
| **Entry Price** | ✅ Working | Captured at order time |
| **Live LTP Update** | ❌ **NOT WORKING** | Shows entry price only |
| **P&L Calculation** | ❌ **NOT WORKING** | Always shows 0.0 |
| **Auto Strategy Trades** | ❌ **NOT WORKING** | Only manual orders |

---

## 🎯 Part 2: Implementing Paper Trading

### **What is Paper Trading?**

**Paper Trading** = Simulated trading without real money
- Tracks strategy signals
- Records virtual trades
- Calculates performance metrics
- **No real orders placed to broker**

---

## 💡 Complete Paper Trading Implementation

### **Architecture:**

```
SignalEngine (generates signals)
        ↓
PaperTradingEngine (NEW!)
        ↓
VirtualPositionManager (tracks paper trades)
        ↓
PerformanceTracker (calculates metrics)
        ↓
Dashboard (displays results)
```

---

### **Step 1: Create PaperTradingEngine**

**File:** `ats-core/src/main/java/com/trading/hf/PaperTradingEngine.java`

```java
package com.trading.hf;

import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PaperTradingEngine implements EventHandler<SignalEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(PaperTradingEngine.class);
    
    private final VirtualPositionManager virtualPositionManager;
    private final Map<String, Double> latestPrices = new ConcurrentHashMap<>();
    private final boolean enabled;
    
    public PaperTradingEngine(VirtualPositionManager virtualPositionManager) {
        this.virtualPositionManager = virtualPositionManager;
        this.enabled = ConfigLoader.getBooleanProperty("paper.trading.enabled", false);
    }
    
    @Override
    public void onEvent(SignalEvent event, long sequence, boolean endOfBatch) {
        if (!enabled) return;
        
        String signalType = event.getType();
        String symbol = event.getSymbol();
        double price = event.getPrice();
        
        logger.info("[PAPER] Signal received: {} for {} at {}", signalType, symbol, price);
        
        // Update latest price for P&L calculation
        latestPrices.put(symbol, price);
        
        // Act on signals
        switch (signalType) {
            case "INITIATIVE_BUY":
            case "STATE_DISCOVERY_UP":
                handleBuySignal(symbol, price, event);
                break;
                
            case "INITIATIVE_SELL":
            case "STATE_DISCOVERY_DOWN":
                handleSellSignal(symbol, price, event);
                break;
                
            case "STATE_ROTATION":
                // Close existing positions on rotation
                closePositions(symbol, price);
                break;
        }
        
        // Update P&L for all open positions
        updatePnL();
    }
    
    private void handleBuySignal(String symbol, double price, SignalEvent event) {
        // Check if we already have a position
        if (virtualPositionManager.hasPosition(symbol)) {
            logger.info("[PAPER] Already in position for {}, skipping BUY", symbol);
            return;
        }
        
        // Calculate position size (example: fixed 50 units)
        int quantity = calculatePositionSize(symbol, price);
        
        // Open virtual position
        virtualPositionManager.openPosition(
            symbol,
            quantity,
            "BUY",
            price,
            System.currentTimeMillis(),
            event.getVah(),
            event.getVal(),
            event.getPoc()
        );
        
        logger.info("[PAPER] ✅ BUY {} x{} @ {}", symbol, quantity, price);
    }
    
    private void handleSellSignal(String symbol, double price, SignalEvent event) {
        // Check if we have a long position to exit
        VirtualPosition position = virtualPositionManager.getPosition(symbol);
        
        if (position != null && "BUY".equals(position.getSide())) {
            // Close long position
            closePosition(symbol, price, "SIGNAL_SELL");
        }
        
        // Could also open short positions here if desired
    }
    
    private void closePosition(String symbol, double price, String reason) {
        VirtualPosition position = virtualPositionManager.getPosition(symbol);
        if (position == null) return;
        
        double pnl = virtualPositionManager.closePosition(symbol, price, System.currentTimeMillis(), reason);
        
        logger.info("[PAPER] ❌ CLOSE {} | Entry: {} | Exit: {} | P&L: {}", 
            symbol, position.getEntryPrice(), price, pnl);
    }
    
    private void closePositions(String symbol, double price) {
        if (virtualPositionManager.hasPosition(symbol)) {
            closePosition(symbol, price, "STATE_ROTATION");
        }
    }
    
    private void updatePnL() {
        for (VirtualPosition position : virtualPositionManager.getAllOpenPositions()) {
            Double currentPrice = latestPrices.get(position.getInstrumentKey());
            if (currentPrice != null) {
                position.updateCurrentPrice(currentPrice);
            }
        }
    }
    
    private int calculatePositionSize(String symbol, double price) {
        // Simple fixed size for now
        // Could be: % of capital, volatility-based, etc.
        return 50;
    }
}
```

---

### **Step 2: Create VirtualPositionManager**

**File:** `ats-core/src/main/java/com/trading/hf/VirtualPositionManager.java`

```java
package com.trading.hf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualPositionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VirtualPositionManager.class);
    
    private final ConcurrentHashMap<String, VirtualPosition> openPositions = new ConcurrentHashMap<>();
    private final List<VirtualTrade> closedTrades = new ArrayList<>();
    private final PerformanceTracker performanceTracker = new PerformanceTracker();
    
    public void openPosition(String instrumentKey, int quantity, String side, 
                            double entryPrice, long entryTime,
                            double vah, double val, double poc) {
        VirtualPosition position = new VirtualPosition(
            instrumentKey, quantity, side, entryPrice, entryTime, vah, val, poc
        );
        openPositions.put(instrumentKey, position);
        
        logger.info("[VIRTUAL] Opened {} position: {} x{} @ {}", 
            side, instrumentKey, quantity, entryPrice);
    }
    
    public double closePosition(String instrumentKey, double exitPrice, long exitTime, String exitReason) {
        VirtualPosition position = openPositions.remove(instrumentKey);
        if (position == null) {
            logger.warn("[VIRTUAL] No position found for {}", instrumentKey);
            return 0.0;
        }
        
        // Calculate P&L
        double pnl = calculatePnL(position, exitPrice);
        
        // Create closed trade record
        VirtualTrade trade = new VirtualTrade(
            position,
            exitPrice,
            exitTime,
            pnl,
            exitReason
        );
        closedTrades.add(trade);
        
        // Update performance tracker
        performanceTracker.recordTrade(trade);
        
        logger.info("[VIRTUAL] Closed position: {} | P&L: {} | Reason: {}", 
            instrumentKey, pnl, exitReason);
        
        return pnl;
    }
    
    private double calculatePnL(VirtualPosition position, double exitPrice) {
        double diff = exitPrice - position.getEntryPrice();
        if ("SELL".equals(position.getSide())) {
            diff = -diff;  // Invert for short positions
        }
        return diff * position.getQuantity();
    }
    
    public boolean hasPosition(String instrumentKey) {
        return openPositions.containsKey(instrumentKey);
    }
    
    public VirtualPosition getPosition(String instrumentKey) {
        return openPositions.get(instrumentKey);
    }
    
    public Collection<VirtualPosition> getAllOpenPositions() {
        return openPositions.values();
    }
    
    public List<VirtualTrade> getAllClosedTrades() {
        return new ArrayList<>(closedTrades);
    }
    
    public PerformanceTracker getPerformanceTracker() {
        return performanceTracker;
    }
}
```

---

### **Step 3: Create VirtualPosition Class**

**File:** `ats-core/src/main/java/com/trading/hf/VirtualPosition.java`

```java
package com.trading.hf;

public class VirtualPosition {
    private final String instrumentKey;
    private final int quantity;
    private final String side;  // "BUY" or "SELL"
    private final double entryPrice;
    private final long entryTime;
    private final double vah;
    private final double val;
    private final double poc;
    
    private double currentPrice;
    private double unrealizedPnL;
    
    public VirtualPosition(String instrumentKey, int quantity, String side,
                          double entryPrice, long entryTime,
                          double vah, double val, double poc) {
        this.instrumentKey = instrumentKey;
        this.quantity = quantity;
        this.side = side;
        this.entryPrice = entryPrice;
        this.entryTime = entryTime;
        this.vah = vah;
        this.val = val;
        this.poc = poc;
        this.currentPrice = entryPrice;
        this.unrealizedPnL = 0.0;
    }
    
    public void updateCurrentPrice(double price) {
        this.currentPrice = price;
        double diff = price - entryPrice;
        if ("SELL".equals(side)) {
            diff = -diff;
        }
        this.unrealizedPnL = diff * quantity;
    }
    
    // Getters
    public String getInstrumentKey() { return instrumentKey; }
    public int getQuantity() { return quantity; }
    public String getSide() { return side; }
    public double getEntryPrice() { return entryPrice; }
    public long getEntryTime() { return entryTime; }
    public double getCurrentPrice() { return currentPrice; }
    public double getUnrealizedPnL() { return unrealizedPnL; }
    public double getVah() { return vah; }
    public double getVal() { return val; }
    public double getPoc() { return poc; }
}
```

---

### **Step 4: Create VirtualTrade & PerformanceTracker**

**File:** `ats-core/src/main/java/com/trading/hf/VirtualTrade.java`

```java
package com.trading.hf;

public class VirtualTrade {
    private final String instrumentKey;
    private final int quantity;
    private final String side;
    private final double entryPrice;
    private final long entryTime;
    private final double exitPrice;
    private final long exitTime;
    private final double pnl;
    private final String exitReason;
    private final double vah, val, poc;
    
    public VirtualTrade(VirtualPosition position, double exitPrice, 
                       long exitTime, double pnl, String exitReason) {
        this.instrumentKey = position.getInstrumentKey();
        this.quantity = position.getQuantity();
        this.side = position.getSide();
        this.entryPrice = position.getEntryPrice();
        this.entryTime = position.getEntryTime();
        this.exitPrice = exitPrice;
        this.exitTime = exitTime;
        this.pnl = pnl;
        this.exitReason = exitReason;
        this.vah = position.getVah();
        this.val = position.getVal();
        this.poc = position.getPoc();
    }
    
    public boolean isWinner() { return pnl > 0; }
    public long getHoldingPeriod() { return exitTime - entryTime; }
    
    // Getters...
    public String getInstrumentKey() { return instrumentKey; }
    public int getQuantity() { return quantity; }
    public String getSide() { return side; }
    public double getEntryPrice() { return entryPrice; }
    public double getExitPrice() { return exitPrice; }
    public double getPnl() { return pnl; }
    public String getExitReason() { return exitReason; }
}
```

**File:** `ats-core/src/main/java/com/trading/hf/PerformanceTracker.java`

```java
package com.trading.hf;

import java.util.ArrayList;
import java.util.List;

public class PerformanceTracker {
    private final List<VirtualTrade> trades = new ArrayList<>();
    private double totalPnL = 0.0;
    private int winners = 0;
    private int losers = 0;
    
    public void recordTrade(VirtualTrade trade) {
        trades.add(trade);
        totalPnL += trade.getPnl();
        if (trade.isWinner()) {
            winners++;
        } else {
            losers++;
        }
    }
    
    public double getTotalPnL() { return totalPnL; }
    public int getTotalTrades() { return trades.size(); }
    public int getWinners() { return winners; }
    public int getLosers() { return losers; }
    public double getWinRate() { 
        return trades.isEmpty() ? 0.0 : (double) winners / trades.size() * 100;
    }
    
    public double getAveragePnL() {
        return trades.isEmpty() ? 0.0 : totalPnL / trades.size();
    }
    
    public double getMaxWin() {
        return trades.stream()
            .filter(VirtualTrade::isWinner)
            .mapToDouble(VirtualTrade::getPnl)
            .max()
            .orElse(0.0);
    }
    
    public double getMaxLoss() {
        return trades.stream()
            .filter(t -> !t.isWinner())
            .mapToDouble(VirtualTrade::getPnl)
            .min()
            .orElse(0.0);
    }
}
```

---

### **Step 5: Wire Up in Main.java**

**Edit:** `ats-dashboard/src/main/java/com/trading/hf/Main.java`

```java
// After line 53
VirtualPositionManager virtualPositionManager = new VirtualPositionManager();
PaperTradingEngine paperTradingEngine = new PaperTradingEngine(virtualPositionManager);

// Update DisruptorManager initialization to include paper trading engine
DisruptorManager disruptorManager = new DisruptorManager(
    questDBWriter,
    rawFeedWriter,
    volumeBarGenerator,
    indexWeightCalculator,
    optionChainProvider,
    thetaExitGuard,
    signalPersistenceWriter,
    orderPersistenceWriter,
    telemetryWriter,
    heavyweightWriter,
    List.of(
        (event, seq, end) -> DashboardBridge.onMarketEvent(event),
        paperTradingEngine  // ADD THIS - processes signals for paper trading
    )
);
```

---

### **Step 6: Update DashboardBridge to Show Virtual Positions**

**Edit:** `DashboardBridge.java` lines 244-256

```java
// 7. Active Trades (Show BOTH real and virtual positions)
List<DashboardViewModel.TradeViewModel> allTrades = new ArrayList<>();

// Real positions
allTrades.addAll(positionManager.getAllPositions().values().stream().map(pos -> {
    DashboardViewModel.TradeViewModel tvm = new DashboardViewModel.TradeViewModel();
    tvm.symbol = getFriendlyName(pos.getInstrumentKey());
    tvm.entry = pos.getEntryPrice();
    tvm.ltp = pos.getEntryPrice();  // TODO: Update with live price
    tvm.qty = pos.getQuantity();
    tvm.pnl = 0.0;  // TODO: Calculate
    tvm.reason = pos.getSide() + " (REAL)";
    return tvm;
}).collect(Collectors.toList()));

// Virtual (paper) positions
if (virtualPositionManager != null) {
    allTrades.addAll(virtualPositionManager.getAllOpenPositions().stream().map(pos -> {
        DashboardViewModel.TradeViewModel tvm = new DashboardViewModel.TradeViewModel();
        tvm.symbol = getFriendlyName(pos.getInstrumentKey());
        tvm.entry = pos.getEntryPrice();
        tvm.ltp = pos.getCurrentPrice();
        tvm.qty = pos.getQuantity();
        tvm.pnl = pos.getUnrealizedPnL();
        tvm.reason = pos.getSide() + " (PAPER)";
        return tvm;
    }).collect(Collectors.toList()));
}

viewModel.activeTrades = allTrades;
```

---

### **Step 7: Add Configuration**

**Edit:** `config.properties`

```properties
# Paper Trading
paper.trading.enabled=true

# Paper Trading Rules
paper.position.size=50
paper.max.positions=5
paper.risk.per.trade=1000.0
```

---

## 📊 Performance Metrics Dashboard

Create a new endpoint to show performance:

**Add to DashboardService.java:**

```java
app.get("/api/performance", ctx -> {
    if (virtualPositionManager != null) {
        PerformanceTracker tracker = virtualPositionManager.getPerformanceTracker();
        ctx.json(Map.of(
            "totalPnL", tracker.getTotalPnL(),
            "totalTrades", tracker.getTotalTrades(),
            "winners", tracker.getWinners(),
            "losers", tracker.getLosers(),
            "winRate", tracker.getWinRate(),
            "avgPnL", tracker.getAveragePnL(),
            "maxWin", tracker.getMaxWin(),
            "maxLoss", tracker.getMaxLoss(),
            "closedTrades", virtualPositionManager.getAllClosedTrades()
        ));
    } else {
        ctx.status(503).json(Map.of("error", "Paper trading not enabled"));
    }
});
```

---

## 🎯 Summary

### **How Active Trades Gets Populated:**
```
Real Orders: UpstoxOrderManager → PositionManager → Dashboard
Paper Trades: SignalEngine → PaperTradingEngine → VirtualPositionManager → Dashboard
```

### **Paper Trading Benefits:**
- ✅ Test strategy without risking capital
- ✅ Track performance metrics
- ✅ See all trades in Active Trades panel
- ✅ Calculate win rate, avg P&L, etc.
- ✅ Works in both live and simulation mode

### **Next Steps:**
1. Create the 5 new Java files
2. Update Main.java and DashboardBridge.java
3. Add `paper.trading.enabled=true` to config
4. Rebuild: `mvn install -DskipTests`
5. Run and watch signals become trades!

---

**Paper trading will automatically convert your signals into virtual trades and track performance! 🚀**
