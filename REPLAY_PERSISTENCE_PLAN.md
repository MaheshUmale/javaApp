# Replay and Performance Persistence Plan

This document outlines the implementation strategy for persisting system-wide telemetry and logic snapshots to enable full-day replay and performance analysis.

## Implementation Order

### Phase 1: Signal Rationale Persistence (High Value)
- **Objective**: Capture the "Why" behind every state change and signal.
- **Data**: VAH, VAL, POC, Cumulative Delta, and Signal Type.
- **Storage**: QuestDB table `signal_logs`.

### Phase 2: Order Lifecycle Persistence (P&L Accuracy)
- **Objective**: Track every order from submission to fill/cancel.
- **Data**: Trigger Price, Fill Price, Slippage, Execution Latency, and Order Status.
- **Storage**: QuestDB table `orders`.

### Phase 3: System Telemetry & Infrastructure Health
- **Objective**: Monitor bottlenecks and network lag.
- **Data**: Processing Latency (End-to-End), Ring Buffer Depth, and Network Delay.
- **Storage**: QuestDB table `telemetry`.

### Phase 4: Divergence & Heavyweight Snapshots
- **Objective**: Analyze correlation between index movements and constituent weightage.
- **Data**: Aggregate Weighted Delta, Individual Heavyweight Price Changes.
- **Storage**: QuestDB table `heavyweight_logs`.

## Status Tracking

| Phase | Description | Status |
| :--- | :--- | :--- |
| 1 | Signal Rationale | [x] Completed |
| 2 | Order Lifecycle | [x] Completed |
| 3 | System Telemetry | [x] Completed |
| 4 | Divergence Logs | [x] Completed |
