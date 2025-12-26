# Project Intent, Architecture, and Design

This document provides a comprehensive overview of the High-Frequency Auction Trading System (HF-ATS). It covers the project's core objectives, system architecture, trading logic, UI/UX design, and future development plans.

---

## 1. The Master Prompt: JULES-HF-Auction-Engine

### Mission Context

We are building a High-Frequency Auction Trading System (HF-ATS) in Java 21 to execute strategies based on **Auction Market Theory**. The system must ingest 250+ tick-level instrument feeds from Upstox API v3, process them using lock-free concurrency, and persist every tick into QuestDB for millisecond-accurate backtesting.

### Core Objective

Implement a high-throughput, event-driven trading backbone using the **LMAX Disruptor** and **Java 21 Virtual Threads**, capable of turning raw binary Protobuf ticks into **Volume-Based Bars** and **Order Book Imbalance (OBI)** signals.

### Phase 1: Infrastructure & Data Ingestion

1.  **Protobuf Integration:** Utilize the provided `marketDataFeed.proto`. Generate the Java source files. Create a `ProtobufDecoder` class that transforms binary `byte[]` packets into a `MarketEvent` POJO.
2.  **WebSocket Client:** Implement a `UpstoxWssClient` using Java 21 `HttpClient` WebSockets.
    *   Must handle the Upstox v3 `authorizedRedirectUri` flow.
    *   Must run on a dedicated thread to ensure zero packet loss.
    *   Must publish every decoded `MarketEvent` to the **LMAX Disruptor Ring Buffer**.
3.  **The Disruptor Backbone:** Setup a `RingBuffer` with a `YieldingWaitStrategy`. Configure a single producer (WSS) and three parallel consumers (DB, Logic, Dashboard).

### Phase 2: Persistence & Storage (QuestDB)

1.  **ILP Implementation:** Implement a `QuestDBWriter` using the **QuestDB Java ILP Client** (NOT JDBC).
    *   Ingest data over TCP/HTTP using Influx Line Protocol.
    *   Configure **WAL (Write-Ahead Log)** and partitioned tables (by Day).
    *   Implement an **Async Flush Strategy**: Commit every 5,000 events or 1 second.
2.  **Schema:** Tables must include `timestamp`, `symbol`, `ltp`, `size`, `side` (aggressor), `bidQ`, `askP`, and `theta`.

### Phase 3: Signal & Bar Generation

1.  **Volume Bar Factory:** Create a `VolumeBarGenerator` that aggregates ticks into bars based on a fixed volume threshold (e.g., 5,000 lots).
    *   Store the state of all 250 instruments in a `ConcurrentHashMap`.
    *   Once a bar is complete, calculate **Cumulative Volume Delta (CVD)** and **Vwap**.
2.  **Auction Signals:**
    *   **OBI:** Calculate `(TotalBidQty - TotalAskQty) / TotalVolume`.
    *   **Theta-Exit:** Implement a monitor that calculates if current `LTP` has eroded more than the accrued `Theta * TimeDelta`.

### Constraints & Guardrails

*   **No Synchronized Blocks:** Use `Atomic` variables or the Disruptor's sequence barriers.
*   **No Garbage Collection Pressure:** Pre-allocate all `MarketEvent` objects in the Ring Buffer. Reuse objects; do not `new` objects inside the tick loop.
*   **Virtual Threads:** Use `Executors.newVirtualThreadPerTaskExecutor()` for housekeeping tasks (heartbeats, API health checks).

### Dynamic Logic & Weightage Engine

*   **Weighted Index Volume Engine:** Calculate **"Index Buy/Sell Pressure"** based on actual index weights.
*   **Dynamic ATM ± 2 Strike Manager:** Maintain a "Sliding Window" of active option subscriptions, automatically updating as the spot price moves.
*   **Order Flow Delta (Aggressor Identification):** Determine if a trade is an aggressive buy or sell based on LTP vs. Bid/Ask.

---

## 2. System Topography and Functional Requirements

### Overall System Topography

*   **Step 1: The Multi-Threaded Ingestion Hub:** Establishes a persistent WebSocket connection to Upstox v3, decoding binary Protobuf streams.
*   **Step 2: The LMAX Disruptor Ring Buffer (The Central Nervous System):** A lock-free messaging backbone that multicasts decoded events to multiple consumers simultaneously.
*   **Step 3: Persistence via QuestDB ILP:** Immediate storage of every raw tick using Influx Line Protocol for zero-latency write operations.

### The Logic Engines (The Pre-Frontal Cortex)

*   **Step 4: The Index Weightage Engine (Nifty50/BankNifty):** Calculates weighted delta for heavyweight stocks.
*   **Step 5: Dynamic ATM ± 2 Strike Manager:** Tracks the Nifty Spot price and updates option subscriptions.
*   **Step 6: Volume-Based Bar Aggregator:** Creates bars based on volume, not time.

### Auction Market Theory (The Brain)

*   **Step 7: Value Area (VA) & POC Calculation:** Uses the 70% distribution rule to identify the value area.
*   **Step 8: Imbalance & Initiative Detection:** Generates signals based on price breaking out of the value area with high delta.
*   **Step 9: Option Chain "Change in OI" Monitor:** Tracks the rate of change in Open Interest for ATM strikes.
*   **Step 10: The Theta-Exit Guard:** Kills trades if "Price Velocity" does not outpace "Time Decay."

### The Wiring & Integration (Wiring the Brain)

*   **Step 11: The "Auction State" Machine:** Maintains a global state for the Index (ROTATION, DISCOVERY, REJECTION).
*   **Step 12: Interface Contract: `SignalEvent`:** The Brain emits a `SignalEvent` with direction, confidence score, and instrument.
*   **Step 13: Order Execution & Slippage Engine:** Converts `SignalEvent` into a `LimitOrder`.
*   **Step 14: Monitoring & Health Dashboard:** Tracks WSS Latency, QuestDB WAL Lag, and Disruptor Buffer Fill Rate.
*   **Step 15: The Final JULES Prompt Interface:** Build components as Separated Concerns.

---

## 3. UI/UX Design and Frontend

### The 6-Widget Design Strategy

1.  **Top Header: The "Vitals" Bar:** High-level summary of system health and market baseline.
2.  **Widget A: The Auction Profile (AMT Core):** A volume profile chart showing VAH, VAL, and POC.
3.  **Widget B: The Weighted Heavyweights (The Engine):** Lists the top 5-7 Nifty stocks with their weighted delta.
4.  **Widget C: Dynamic Option Chain (The Sniper):** A sliding window of ATM ± 2 strikes.
5.  **Widget D: Sentiment & Trigger Alerts (The Logic):** Translates the "Brain" into human-readable alerts.
6.  **Widget E: Trade Panel & Theta-Guard:** A countdown timer and live P&L for open trades.

---

## 4. Trading Strategy and Scenarios

### The "Sequence of Interest" Checklist

#### 1. The Macro Filter (The "Where")
*   [ ] **Location Check:** Is Nifty outside the previous day's **Value Area**?
*   [ ] **The "Put Wall" Check:** Have we bounced off or held a high-OI Put Strike?

#### 2. The Momentum Filter (The "Who")
*   [ ] **The Heavyweight Engine:** Are Reliance and HDFC Bank aligned with my direction?
*   [ ] **Weighted Delta:** Is the cumulative buy-side aggression increasing on the "Ask"?

#### 3. The Sentiment Filter (The "Why")
*   [ ] **Change in OI:** Is Put OI increasing at the ATM strike during the rise?
*   [ ] **PCR Divergence:** Is the Put-Call Ratio moving *with* the price?

#### 4. The Execution Filter (The "When")
*   [ ] **Strike Selection:** Am I buying **ATM or ATM-1**?
*   [ ] **Entry Signal:** Has a 3 or 5-minute candle closed above the **Opening Range** with high volume?
*   [ ] **Theta Guard:** Is my "Time-Stop" (e.g., 15-20 mins) set in my mind?

---

## 5. Summary & Recent UI Improvements

The JULES-HF-ATS is a high-performance bridge between Macro Auction Context and Micro Execution Detail.

### Recent Enhancements [PHASE 5]
1.  **Symmetrical Option Chain**: Reorganized UI to center on the Strike Price, with Call/Put data mirrored for intuitive analysis.
2.  **Auction Profile Scaling**: Implemented price-relative scaling to prevent 0-axis jumps for high-value instruments like Nifty.
3.  **Data Flow Verification**: Ensured all widgets (Sentiment, Heavyweights, Trades) receive consistent 1Hz updates from the `DashboardBridge`.
4.  **UX Polish**: Added informative placeholders for widgets awaiting live data (e.g., Active Trades).
