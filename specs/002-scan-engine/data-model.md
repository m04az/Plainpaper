# Data Model: Scan Engine (002)

All types are immutable Kotlin values unless noted. Pure models that other features need
live in `:core:domain`; engine-specific types live in `:core:scanengine` `commonMain`.
Coordinates are in **input-photo pixels**, origin top-left, `x` to the right, `y` down.

## `:core:domain` models

### PageSize (enum)

| Value | Pixels at 300 DPI (portrait) | Notes |
|-------|------------------------------|-------|
| `A4` | 2480 × 3508 | Default everywhere except the Letter regions |
| `Letter` | 2550 × 3300 | Default for region codes `US`, `CA`, `MX`, `PH` |
| `Natural` | long side 3508, short side = 3508 / detected aspect, rounded to even | Keeps the detected page's proportions (receipts, Feature 7) |

- `fun PageSize.Companion.defaultFor(regionCode: String?): PageSize` — case-insensitive
  ISO 3166-1 alpha-2; null/unknown → `A4`.
- `fun PageSize.outputPixels(detectedAspect: Double, landscape: Boolean): PixelSize` —
  for A4/Letter swaps width/height when `landscape`; for Natural computes from the aspect
  (aspect = width / height of the corrected page, clamped to 0.2…5.0).

### OutputStyle (enum)

`Scan`, `Grayscale`, `ColorDocument`, `Original`. `channels`: 1 for Scan/Grayscale, 4
(RGBA) for ColorDocument/Original.

### DeviceCapability (enum) and classification

`Full`, `Simplified`.

```
classifyCapability(totalMemoryBytes: Long, is64Bit: Boolean, isLowRamDevice: Boolean): DeviceCapability
  = Full when totalMemoryBytes >= 4 GiB && is64Bit && !isLowRamDevice, else Simplified
```

## `:core:scanengine` models

Spec entity mapping: **ScanRequest** (spec) = `ScanPhoto` + `AnalyzeOptions` + the calling
coroutine (its cancellation is the "cancellation handle"); **ScanResult** = `ScanAnalysis`
+ `ScanOutput` + `ScanReport`.

### ScanPhoto (expect class, platform-backed)

| Field | Type | Constraint |
|-------|------|------------|
| `width`, `height` | Int | 640 ≤ long edge ≤ 6000 after decoder capping (12 MP max); otherwise `Outcome.InvalidInput` |
| backing | Android `Bitmap` (ARGB_8888) / iOS `CGImage` / JVM `BufferedImage` (TYPE_INT_ARGB) | Pixels read in place by the bridge; never copied into Kotlin |

### AnalyzeOptions

| Field | Type | Default | Constraint |
|-------|------|---------|------------|
| `userCorners` | `Quad?` | null | When set, detection is skipped and `confidence = High`, `source = User` |
| `capability` | `DeviceCapability?` | null (= provider's value) | Override for benchmarks/diagnostics |
| `workingLongEdge` | Int? | null (= 2000 Full / 1500 Simplified) | 800…3000 |
| `allowFlatten` | Boolean | true | Ignored (treated as false) when capability is Simplified |

### Quad / Point

`Point(x: Float, y: Float)`; `Quad(topLeft, topRight, bottomRight, bottomLeft)` with
`isConvex` and `area` helpers. Validation: all points inside the photo bounds (±2%
tolerance), convex, area ≥ 5% of the photo, else `Outcome.InvalidInput("corners")`.

### Confidence (enum)

`High`, `Low`, `NotFound` — rules in research R5 step 2. `NotFound` always comes with
`detectedCorners = full photo frame` so the caller has a default rectangle to adjust.

### ProcessingStep (enum) and StepStatus

Steps, in pipeline order: `Decode`, `Detect`, `Perspective`, `Deskew`, `Orient`, `Flatten`,
`Illumination`, `Render`, `Resample`. `StepStatus`: `Applied`, `Skipped(reason)`,
`Failed(reason)`. Skip reasons: `Capability`, `InsufficientLines`, `NoImprovement`,
`UserCorners`, `NotNeeded`.

### StepTiming

`step: ProcessingStep`, `status: StepStatus`, `durationMicros: Long` (0 when skipped).

### Outcome (sealed)

| Variant | Meaning | Caller action |
|---------|---------|---------------|
| `Success` | result delivered | show |
| `Cancelled` | caller cancelled; no result | none |
| `InvalidInput(detail)` | unreadable/too small image, bad corners | explain, pick another photo |
| `TooDark` | median luminance below threshold; result is still delivered (cleaned frame, `confidence = Low/NotFound`) | offer retake |
| `OutOfMemory` | native allocation failed twice (after the retry) | explain, suggest closing apps |
| `Internal(code)` | unexpected native error (`cv::Exception`) | explain generically; code logged |

Exceptions never escape `ScanEngine`; `CancellationException` is rethrown as normal
coroutine cancellation and maps to `Cancelled` only in the report.

### ScanAnalysis (class, `Closeable`)

Result of `analyze`. Holds the native session handle.

| Field | Type | Notes |
|-------|------|-------|
| `corners` | `Quad` | detected or user corners, photo coordinates |
| `cornerSource` | `Detected` / `User` / `FullFrame` | |
| `confidence` | `Confidence` | |
| `rotationApplied` | 0 / 90 / 180 / 270 | from orientation |
| `orientationConfidence` | `High` / `Low` / `None` | None when fewer than 3 text lines |
| `detectedAspect` | Double | width / height of the corrected page |
| `isLandscape` | Boolean | detectedAspect > 1 |
| `capabilityUsed` | `DeviceCapability` | after any retry downgrade |
| `workingLongEdgeUsed` | Int | |
| `flags` | set of `InvertedDocument`, `SpecularHighlight`, `RetriedAtLowerResolution` | |
| `steps` | `List<StepTiming>` | Decode…Illumination |
| `outcome` | `Outcome` | `Success` or `TooDark` (others never produce an analysis) |

Lifecycle: `Created` → (`render` any number of times) → `Closed` (`close()` frees the
native session; subsequent `render` → `IllegalStateException`). `DefaultScanEngine`
registers the handle with a `Cleaner`/`finalize`-free policy: callers must close; Feature 4
will tie it to the ViewModel scope.

### RenderOptions

`style: OutputStyle`, `pageSize: PageSize`, `fit: FitMode = Fit` (`Fit` = centred on white
margins; `Natural` page size ignores it).

### ScanOutput (expect class)

| Field | Type |
|-------|------|
| `width`, `height` | Int (exactly `pageSize.outputPixels(...)`) |
| `channels` | 1 or 4 |
| `styleUsed` | `OutputStyle` (differs from requested only on the inverted-document fallback) |
| `fallbackReason` | `InvertedDocument?` |
| `steps` | `List<StepTiming>` (Render, Resample) |
| platform image | Android `Bitmap` (ALPHA_8 for 1-channel, ARGB_8888 for 4) / iOS `CGImage` / JVM `BufferedImage` |

### ScanReport (value for diagnostics / benchmarks)

Combines `ScanAnalysis` metadata + all `StepTiming`s + `totalMicros` + `peakNativeBytes`
(from the native allocator high-water mark) — used by FR-019, the device benchmark and the
suite's timing rows.

### CapabilityProvider (interface)

`fun current(): DeviceCapability` + `fun details(): CapabilityDetails(totalMemoryBytes,
is64Bit, isLowRamDevice, platformName)`. Actuals for Android, iOS and host.

## `:tools:scansuite` models (serialised, see contracts/reference-manifest.md)

### ReferencePhoto

| Field | Type | Required |
|-------|------|----------|
| `id` | string, `^[a-z0-9-]+$`, unique | yes |
| `file` | relative path under `photos/` | yes |
| `tags` | set of `Category` | yes, ≥ 1 |
| `language` | `ar`, `en`, `fr`, `mixed`, `none` | yes |
| `expectedCorners` | 4 points or `null` (no page) | yes (null allowed) |
| `expectedRotation` | 0/90/180/270 the engine must apply to make it upright | default 0 |
| `transcript` | relative path under `transcripts/` | when legibility is scored |
| `cleanReference` | relative path under `clean/` | required when `transcript` is set |
| `competitors` | map app → relative path under `competitors/<app>/` | optional |
| `device`, `notes` | strings | optional |

`Category` = `id-card`, `receipt`, `curled`, `folded`, `dark-room`, `hard-shadow`,
`low-contrast-background`, `arabic`, `english`, `french`, `mixed-script`, `stamp-signature`,
`no-page`, `rotated`, `pencil`, `inverted`, `flash-highlight`, `open-book`,
`page-fills-frame`.

### MetricRow (per photo)

`id`, `cornerError` (fraction of diagonal, null for no-page), `classifiedNotFound`,
`whiteness` (0…1), `textDarkness` (0…1), `legibility` (0…1 relative, null when skipped),
`straightnessDeg`, `orientationCorrect`, `hostMillis`, `competitor: Map<app, {whiteness,
legibility}>`.

### QualityReport

`rows`, `aggregates` (one per threshold), `thresholds`, `passed: Boolean`,
`competitorSummary` (per app: matchesOrBeats count / total, worst relative delta),
`cancelLatencyMillisP100`, `generatedAt`, `engineVersion` (git SHA).
