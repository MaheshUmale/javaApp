
# Bolt's Journal ⚡

A log of critical performance learnings. Every millisecond counts.

*Measure first, optimize second.*


BOLT'S JOURNAL - CRITICAL LEARNINGS ONLY:

- **Rejected Change (2025-12-26):** Attempted to optimize `MySampleDataReplayer.java` by hoisting a data transformation out of a loop.
  - **Lesson:** The optimization was flawed because the loop was I/O-bound (`Thread.sleep`), not CPU-bound. The change introduced a significant memory regression (doubling memory usage) for no measurable performance gain.
  - **Rule:** Always identify the true bottleneck before optimizing. An optimization that increases memory pressure without a significant, measurable performance win is a net loss. Measure first, optimize second.

