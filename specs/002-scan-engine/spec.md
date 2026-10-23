# Feature Specification: Scan Engine

**Feature Branch**: `002-scan-engine`

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "Scan engine. Given one photo of a paper document, produce a page that looks like it came from a real flatbed scanner. Steps: detect the page edges (with a fallback when detection fails), correct perspective so the page is a perfect rectangle, flatten curled or folded pages when the device is capable, remove shadows and uneven lighting, make the paper pure white while keeping text sharp and dark (including faint pen and stamps), correct small tilts and upside-down pages, and output at a true page size (A4 by default, Letter in US/Canada/Mexico/Philippines) at 300 DPI. Output styles: Scan (black & white), Grayscale, Color document (white background but keeps stamps, signatures and logos in color), Original (light cleanup only). Processing must be fully on-device, cancellable, and fast enough to feel instant on a mid-range phone (target under 2 seconds per page) and must not crash on low-end phones (fall back to simpler steps). Quality is verified against a reference set of real photos (IDs, receipts, curled pages, dark rooms, Arabic/English/French text) with measurable metrics, and must match or beat leading competitor apps on most of them."

## Overview

The scan engine is the core of Plainpaper: a self-contained processing capability that turns
one photo of a paper document into a clean page image that looks like it came from a flatbed
scanner. This feature delivers the engine, its quality reference set and its measurement
harness. It has no screens of its own; the camera (Feature 3) and the review screen
(Feature 4) will call it. Everything runs on the device and nothing leaves it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A photo becomes a clean scanned page (Priority: P1)

A person has a photo of a page taken with their phone, slightly tilted, with a shadow across
one corner and a grey cast from room lighting. The engine finds the page, straightens it into
a perfect rectangle, removes the shadow and the cast, turns the paper pure white, keeps the
text crisp and dark, and delivers an image at a true page size at print resolution. The
result reads like the page was laid on a scanner glass.

**Why this priority**: This is the product's promise. Every other feature is a way to get
photos in and clean pages out.

**Independent Test**: Run the engine on the reference set and inspect the outputs: pages
are rectangular, white, legible, and the automated quality metrics meet their thresholds.

**Acceptance Scenarios**:

1. **Given** a photo of an A4 page on a desk taken at an angle, **When** it is processed with
   the Scan style, **Then** the output is a rectangle with the page's aspect ratio, the paper
   is white, the text is black and legible, and no desk is visible.
2. **Given** a photo with a hard shadow across half the page, **When** processed, **Then** the
   shadowed half is as white as the lit half and the text under the shadow is as legible.
3. **Given** a photo of a page with faint pencil notes and a light-blue stamp, **When**
   processed with the Scan style, **Then** the pencil and the stamp remain visible.
4. **Given** a photo that is rotated 90° or upside down, **When** processed, **Then** the
   output is upright so the text reads normally.
5. **Given** a photo where the page is tilted by a few degrees inside its detected rectangle,
   **When** processed, **Then** the text lines are horizontal in the output.
6. **Given** any processed page, **When** its size is checked, **Then** it matches the chosen
   page size at 300 dots per inch (A4: 2480 × 3508; Letter: 2550 × 3300).

---

### User Story 2 - Choose an output style (Priority: P2)

The same photo can be rendered in four styles: Scan (pure black and white), Grayscale, Color
document (white paper, but stamps, signatures and logos keep their colour), and Original
(the straightened photo with only light cleanup). Switching style re-renders the page from
the same input without re-detecting the page.

**Why this priority**: Users need Scan for most paperwork, Color for stamped and signed
documents, and Original when the cleanup removes something they wanted.

**Independent Test**: Process one reference photo in each style and confirm the four outputs
differ as described, with identical geometry.

**Acceptance Scenarios**:

1. **Given** a processed page, **When** the Color document style is chosen, **Then** the paper
   is white and a red stamp on it is still red.
2. **Given** a processed page, **When** the Grayscale style is chosen, **Then** no colour
   remains and tonal detail (such as a photo on an ID) is preserved.
3. **Given** a processed page, **When** the Original style is chosen, **Then** the page is
   straightened and lightly brightened but texture, colour cast and background are kept.
4. **Given** a page already processed in one style, **When** another style is requested,
   **Then** the new output has exactly the same pixel dimensions and page boundary.

---

### User Story 3 - It works when the photo is hard (Priority: P3)

Photos are not always good. The page edges may be hidden by a similar-coloured desk or by a
hand, the room may be dark, the page may be curled or folded, or the photo may contain no page
at all. The engine still returns a usable result and tells the caller how confident it is, so
the review screen can offer manual corner adjustment.

**Why this priority**: Many users photograph documents on beds, carpets and in dim rooms.
Failing gracefully is the difference between a usable product and one that is abandoned.

**Independent Test**: Run the hard subsets of the reference set (dark room, low-contrast
background, curled, folded, no page) and confirm each returns a result with the expected
confidence classification and no crash.

**Acceptance Scenarios**:

1. **Given** a photo where the page edges cannot be found, **When** processed, **Then** the
   engine returns the whole photo cleaned up, marks detection as "not found", and provides a
   default rectangle the user can adjust.
2. **Given** a photo of a curled page on a capable device, **When** processed, **Then** the
   text lines in the output are straight to within the flatness threshold.
3. **Given** the same curled page on a low-capability device, **When** processed, **Then** the
   engine skips flattening, still returns a perspective-corrected page, and reports which
   steps were skipped.
4. **Given** a very dark photo, **When** processed, **Then** the output is still white paper
   with readable text, or the engine reports "too dark" so the caller can ask for a retake.
5. **Given** corner points supplied by the user (manual crop), **When** the page is
   re-processed, **Then** those corners are used instead of automatic detection.

---

### User Story 4 - Fast, cancellable and safe on every phone (Priority: P4)

Processing feels instant on a mid-range phone and never freezes or crashes on a low-end one.
The caller can cancel at any time (for example when the user leaves the screen or picks a
different photo), and cancellation stops the work promptly and frees its memory.

**Why this priority**: Speed is part of the promise ("feels instant"); crashes on cheap
phones would hit the core audience hardest.

**Independent Test**: Run the benchmark on the reference set on a mid-range and a low-end
device profile and check the timing and memory numbers; start a processing job and cancel it
mid-way and confirm it stops and releases memory.

**Acceptance Scenarios**:

1. **Given** a 12-megapixel photo on a mid-range phone, **When** processed in the Scan style,
   **Then** the result is ready in under 2 seconds.
2. **Given** processing in progress, **When** the caller cancels, **Then** the work stops
   within 200 milliseconds and no result is delivered.
3. **Given** a low-end phone (2 GB of memory), **When** 20 photos are processed one after
   another, **Then** none crashes the app and each completes within 6 seconds.
4. **Given** a photo larger than the engine's working size, **When** processed, **Then** it
   is scaled down for analysis without loss of output quality beyond the metric thresholds.

---

### User Story 5 - Quality is measured, not guessed (Priority: P5)

As the developer, I maintain a reference set of real photos with expected results. Every
change to the engine is checked automatically against the set with measurable metrics, and
the current numbers are compared with outputs from leading competitor apps on the same
photos. A change that lowers a metric below its threshold cannot be merged.

**Why this priority**: The constitution requires scan-engine changes to be verified against a
fixed reference set with measurable metrics. Without it, quality drifts silently.

**Independent Test**: Run the reference suite; it reports per-photo and aggregate metrics,
fails when a threshold is missed, and prints a comparison table against the stored
competitor outputs.

**Acceptance Scenarios**:

1. **Given** the reference set, **When** the suite runs, **Then** every photo produces a
   result and a metric row, and the suite passes only when all aggregate thresholds hold.
2. **Given** a competitor output stored for a photo, **When** the suite runs, **Then** the
   table shows Plainpaper's metric next to the competitor's for that photo and an overall
   "matches or beats" count.
3. **Given** an engine change that reduces text legibility on dark-room photos below the
   threshold, **When** the suite runs in the pull request, **Then** the pull request fails.

---

### Edge Cases

- The photo contains two pages (an open book): the engine picks the larger page-like
  quadrilateral and reports low confidence so the user can adjust.
- The page fills the entire photo (edges outside the frame): detection returns the full frame
  and the result is the cleaned photo at the chosen page size.
- The page is a long receipt whose aspect ratio is far from A4: with the default page size the
  content is placed on the page with white margins; with the "natural" page size option the
  output keeps the receipt's proportions (Feature 7 depends on this).
- The document is dark paper with light text (an inverted design): the Scan style must not
  invert it into unreadable noise; the engine falls back to Grayscale behaviour for that page
  and reports it.
- Extremely small text (6 pt) on a 12-megapixel photo: remains legible at 300 DPI.
- Photo taken with flash producing a specular highlight: the highlight area is treated as
  paper white and text inside it is recovered where any contrast remains.
- A corrupt or unreadable image file: the engine returns a clear error, never a crash.
- Memory pressure during processing: the engine reduces its working resolution and retries
  once before reporting failure.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The engine MUST accept one photo (JPEG, HEIC or raw pixel data) plus options
  (output style, page size, optional user-supplied corner points) and return a result
  containing the page image, the detected corner points, a confidence level (high, low, not
  found) and the list of processing steps applied or skipped.
- **FR-002**: The engine MUST detect the four corners of the dominant page in the photo. When
  no page is found it MUST fall back to the full photo frame and report confidence "not found".
- **FR-003**: The engine MUST correct perspective so the output page is a rectangle whose
  aspect ratio matches the chosen page size, and MUST accept user-supplied corners in place of
  detection.
- **FR-004**: On devices that meet the capability threshold the engine MUST flatten curled or
  folded pages so that text baselines are straight; on other devices it MUST skip this step and
  report it as skipped.
- **FR-005**: The engine MUST remove shadows and uneven lighting so paper luminance is uniform
  across the page.
- **FR-006**: The engine MUST render paper as pure white and text as dark in the Scan,
  Grayscale and Color document styles, while preserving faint pencil, light-coloured ink and
  stamps.
- **FR-007**: The engine MUST correct residual tilt of up to ±5° after perspective correction
  and MUST orient the page upright, including 90°, 180° and 270° rotations, using the text
  direction. Orientation MUST work for Arabic, Latin and mixed-script pages.
- **FR-008**: The engine MUST output at 300 DPI at the chosen page size: A4 by default,
  Letter when the device region is US, Canada, Mexico or the Philippines, plus a "natural"
  option that keeps the detected page's proportions. The default is supplied by the caller
  from the user's settings; the engine itself takes an explicit page size.
- **FR-009**: The engine MUST provide four styles: Scan (black and white), Grayscale, Color
  document (white paper, colour preserved for stamps, signatures and logos), and Original
  (straightened with light cleanup only). Re-rendering in another style MUST reuse the
  detection and geometry of the first pass.
- **FR-010**: All processing MUST happen on the device with no network access.
- **FR-011**: Processing MUST be cancellable; cancellation MUST stop work within 200 ms and
  release the memory used.
- **FR-012**: The engine MUST complete a 12-megapixel photo in the Scan style within 2 seconds
  on the mid-range reference device and within 6 seconds on the low-end reference device.
- **FR-013**: The engine MUST never crash the app on low-end devices: it MUST detect device
  capability (memory, processor class) and choose a simpler pipeline, and MUST recover from
  memory pressure by reducing working resolution and retrying once.
- **FR-014**: The engine MUST report failures (unreadable input, too dark, out of memory
  after retry) as distinct, user-explainable outcomes rather than exceptions that escape to the
  caller.
- **FR-015**: A reference set of real photos MUST exist in the repository, covering at least:
  IDs and cards, receipts, curled pages, folded pages, dark rooms, hard shadows, low-contrast
  backgrounds, Arabic text, English text, French text, mixed Arabic/English, stamps and
  signatures, photos with no page. Each photo MUST have an expected corner rectangle and,
  where applicable, a ground-truth text transcription for legibility scoring.
- **FR-016**: An automated quality suite MUST run the engine on the whole reference set and
  compute per-photo metrics: corner detection error, paper whiteness, text contrast,
  legibility (text recognition accuracy against the transcription), straightness of text
  lines, and processing time. Aggregate thresholds MUST be defined and the suite MUST fail
  when any threshold is missed.
- **FR-017**: For each reference photo that has a stored competitor output, the suite MUST
  compute the same metrics on the competitor output and report per-photo and overall
  "matches or beats" results.
- **FR-018**: The quality suite MUST run on every pull request that touches the engine, and a
  pull request that lowers an aggregate metric below its threshold MUST NOT be mergeable.
- **FR-019**: The engine MUST expose its capability decision (full or simplified pipeline) and
  timing per step so the app can show diagnostics and the suite can benchmark.
- **FR-020**: The engine MUST have unit tests for every pure decision it makes (capability
  classification, page-size resolution, orientation decision, style selection, fallback
  logic) in addition to the reference suite.

### Key Entities

- **ScanRequest**: the input photo reference, output style, page size, optional user corners,
  and a cancellation handle.
- **ScanResult**: the output page image, detected corners (in input-photo coordinates),
  confidence (high, low, not found), applied and skipped steps, per-step timings, and an
  outcome (success or a named failure).
- **OutputStyle**: Scan, Grayscale, ColorDocument, Original.
- **PageSize**: A4, Letter, Natural; carries the output pixel dimensions at 300 DPI.
- **DeviceCapability**: Full or Simplified, derived from memory and processor class.
- **ReferencePhoto**: a photo file, its category tags, expected corners, optional ground-truth
  text, and optional competitor outputs (one image per competitor).
- **QualityReport**: per-photo metric rows, aggregates, threshold pass/fail, and the
  competitor comparison table.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the reference set, the detected page corners are within 2% of the image
  diagonal of the expected corners for at least 90% of photos that contain a page, and 100%
  of "no page" photos are classified as not found.
- **SC-002**: In the Scan style, median paper luminance is at least 97% of white and median
  text luminance at most 25% of white on at least 95% of reference pages.
- **SC-003**: Legibility: text recognition accuracy on processed pages is at least 95% of the
  accuracy measured on the ground-truth-aligned clean reference, averaged over Arabic, English
  and French subsets, with no subset below 90%.
- **SC-004**: Text baselines in processed curled pages deviate from horizontal by at most
  0.5° on capable devices.
- **SC-005**: Median processing time for a 12-megapixel photo is under 2.0 seconds on the
  mid-range reference device and under 6.0 seconds on the low-end reference device; the 95th
  percentile is under 3.0 and 9.0 seconds respectively.
- **SC-006**: Zero crashes and zero unhandled failures across 200 consecutive processing runs
  on the low-end reference device.
- **SC-007**: Cancellation stops processing within 200 ms in 100% of attempts, and memory
  returns to the pre-processing baseline within 1 second.
- **SC-008**: Plainpaper's legibility and whiteness metrics match or beat the best stored
  competitor output on at least 60% of the reference photos that have competitor outputs, and
  are never more than 10% worse on any photo.
- **SC-009**: The quality suite runs in the pull-request pipeline and fails a pull request that
  misses a threshold, demonstrated once with a deliberate regression.

## Assumptions

- The engine is delivered as a shared capability with no user interface; the camera (Feature
  3) and review (Feature 4) screens integrate it. A developer-only test entry (reference
  suite and a benchmark) is enough to validate it.
- Reference devices: mid-range = a 2022–2023 phone with 6 GB of memory and an upper-mid
  processor; low-end = a 2019–2021 phone with 2–3 GB of memory. Exact models are chosen in
  the plan and recorded with the benchmark results. On CI the suite runs on an emulator or
  simulator for correctness, while timing targets are validated on physical devices.
- The reference set is provided by the project owner from real-world photos they have the
  right to use; identity documents in the set are the owner's own or samples that contain no
  real personal data. The set lives in the repository (or in a versioned archive the suite
  downloads at build time if it grows beyond a few hundred megabytes).
- Competitor outputs are produced manually, once, by scanning each reference photo with the
  chosen competitor apps and exporting at the same page size; they are stored alongside the
  reference photo and never updated automatically.
- Capability threshold default: a device is "capable" of page flattening when it reports at
  least 4 GB of memory and a 64-bit processor; otherwise it uses the simplified pipeline.
- "Legibility" is measured with the on-device text recognition capability planned for
  Feature 8 where available, and with a desktop text recogniser in the quality suite; the
  metric compares recognised text to the stored transcription.
- Faint content preservation is judged by the legibility metric on the pencil/stamp subset
  and by the Color document colour-retention check (stamp pixels keep their hue).
- The engine does not store any input or output image itself; callers own persistence
  (Feature 6).
- Page size defaults by region come from the Settings default (Feature 10 adds the user-facing
  option); this feature only consumes an explicit page size.
