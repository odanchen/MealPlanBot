# Pi performance acceptance: pending

No actual Pi measurements were available during the 2026-10-18 implementation.
T100/T110/T125 remain unchecked. The ≤400 MiB RSS budget is a target, not a result.

After recovery and hardening checks, run `scripts/measure-household-load.sh` on the
actual arm64 Pi with ten disposable member sessions and 500 active entries. Record
cold start to ready health, idle and peak RSS/CPU, read/action p95 (target ≤2 seconds),
SQLite busy failures/contention, SD growth over a representative interval, and exact
runtime/OS/build identity. The script records concurrent HTTP timings and Linux
resource/disk counters and refuses another architecture.

Run connected two-browser shared-shopping checks on the target (≤5-second visibility),
hidden-view polling cessation/immediate refresh, and reconnect behavior. Local browser
results do not replace these target measurements. Do not rely on swap to pass.
