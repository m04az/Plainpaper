# Results: Scan Engine (002)

Numbers come from the generated reports; this file is updated whenever the gate or the device
benchmarks change. Real-photo rows appear once the owner adds `reference-set/` photos.

## Quality gate (synthetic set, host, 2026-10-23)

| Check | Value | Threshold |
|-------|-------|-----------|
| Corners within 1% of the diagonal | 100% (33/33; curled pages held to 2%) | ≥ 100% |
| Upright orientation (Arabic, English, French; 0/90/180/270; curled; dark; low contrast) | 100% | ≥ 100% |
| Scan style: paper ≥ 0.97 white and ink ≤ 0.25 | 100% (35/35) | ≥ 95% |
| Legibility relative to the clean reference (Tesseract 5, ara/eng/fra) | 1.02 average (Arabic 1.07, English 0.99, French 1.00) | ≥ 0.95 |
| No-page photos classified "not found" | 2/2 | all |
| Very dark photo reported "too dark" | yes | yes |
| Curled pages flattened (Full), baseline deviation | 0.30° / 0.49° | ≤ 0.5° |
| Simplified pipeline | all photos succeed, flattening skipped, peak < 70 MB | |
| Peak OpenCV memory, Full pipeline (3 MP photos) | 55 MB after banding (was 179 MB) | < 120 MB |
| Cancellation latency, 50 random cut-offs | p100 9 ms | ≤ 200 ms |
| Out-of-memory handling | retried once, then reported; recovery on the next call | |

Host stage timings for a 3 MP synthetic photo (Apple M5 Pro): detect 25 ms, deskew 43 ms,
perspective 8 ms, orientation 17 ms, illumination 9 ms, Scan render 17 ms, Grayscale 3 ms,
Color document 6 ms, Original 3 ms.

## Real reference photos

Pending: the owner's photo set (`scripts/scanref/README.md`). The per-category checks switch
from informational to enforced once a category has three photos.

## Device benchmarks

Reference devices pending (see `reference-set/benchmarks/README.md`). Harness check on the
development emulator (`sdk_gphone16k_arm64`, API 37, 4 GB class → Simplified pipeline, 3 MP
synthetic photos): 20 consecutive runs, median 582 ms, p95 682 ms, native heap delta ≤ 8.8 MB one
second after each run, zero failures (`reference-set/benchmarks/sdk_gphone16k_arm64-20261023.json`).
The emulator smoke test (`SmokeDeviceTest`) passes on the same image.

## Coverage

Kover merged line coverage with `:core:scanengine` included: 92.0 % (gate ≥ 80 %, `koverVerify`
passes).

## Competitor comparison

Pending: competitor exports from Adobe Scan, Microsoft Lens and CamScanner (owner, task T089).
