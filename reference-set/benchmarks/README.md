# Device benchmarks

Timing and memory results of the scan engine on reference devices (spec 002, SC-005/SC-006/SC-007;
research R16). Each file is `<device-model>-<date>.json` as written by the benchmark harness.

| Profile | How it is captured |
|---------|--------------------|
| Mid-range Android (owner's phone, 6 GB class) | `scripts/bench-android.sh` with the phone connected: pushes up to 10 reference photos, runs `BenchmarkTest` (`core/scanengine/src/androidDeviceTest`), pulls `bench.json` |
| Low-end profile (Android emulator, 2 GB RAM, 2 cores, API 30) | `scripts/bench-android.sh --low-end` creates/starts the `plainpaper-lowend` AVD first |
| iPhone (owner's device) | Run `ScanBenchmarkTests` in `iosApp/iosAppTests` from Xcode on the device with JPEGs added to the test bundle; copy the printed JSON here. CI only compiles this target (`build-for-testing`) |
| Soak (SC-006) | `scripts/bench-android.sh --low-end --iterations 20` over 10 photos = 200 consecutive runs; zero failed runs expected |

Each run records `analyzeMs`, `renderMs`, `totalMs`, the outcome and confidence, the native
peak bytes reported by the engine and the native heap delta one second after the run. The
targets are a median under 2.0 s (p95 3.0 s) on the mid-range phone and under 6.0 s (p95 9.0 s)
on the low-end profile for a 12-megapixel photo in the Scan style.
