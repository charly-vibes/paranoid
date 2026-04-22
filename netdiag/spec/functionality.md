# NetDiag — Functionality Specification

## Purpose

Diagnoses Android network connectivity issues by capturing snapshots of Wi-Fi, cellular, DNS, and routing state, comparing them over time, and surfacing actionable insights without requiring root.

## Core Goals

- Snapshot-based diagnostics: capture a full picture of network state on demand
- Compare snapshots to detect regressions or improvements
- Active probing: ICMP ping, DNS resolution timing, HTTP latency attribution
- Cellular signal quality (RSRP, RSRQ, RSSNR) via `TelephonyManager`
- Wi-Fi channel analysis (congestion, BSS load, link rate vs. max rate)
- Session history: browse, compare, and share past diagnostic runs
- Offline-first: all data stored locally with Room

## Non-Goals (v1)

- Continuous background monitoring
- Root / Shizuku integration
- Speed test against external servers
- Cross-device comparison or cloud sync
