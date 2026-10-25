# Feature Specification: Smart document detection

**Feature Branch**: `004-smart-detection`

**Created**: 2026-10-24

**Status**: Draft

**Input**: User description: "Smart document detection. Two ways to find the page reliably, like CamScanner. (1) Our own camera gets a small on-device model that finds the document outline in live preview frames and in the captured photo, robust to clutter: a card or page held in a hand, cables crossing it, a light floor or desk the same colour as the paper, busy backgrounds. The outline follows the document without flicker and auto-capture fires when it is steady; the classic detector stays as the fallback (low-end devices, model missing). The model is ours (trained on synthetic composites we generate), so its weights can ship in a paid app; it runs inside the existing scan engine on Android and iOS, adds at most a few MB, and keeps live detection fast enough for a smooth outline. (2) A "Quick scan" option that opens the phone's built-in document scanner (Google's on Android, Apple's on iOS) for people who want it; the pages it returns join the same capture session so looks, rotate and flip still apply. Nothing leaves the device: no network permission is added (telemetry-only permissions from libraries are removed), and Quick scan is hidden when the phone has no built-in scanner."

## Overview

Phone tests of Feature 003 showed that outline detection based only on edges and brightness
fails in ordinary scenes: an ID card held in a hand, a cable lying across the page, a white page
on a light floor, a busy desk. The outline then hugs the wrong shape or the whole frame, so
auto-capture never fires and the crop is wrong. This feature makes detection reliable in two
complementary ways: a document-finding model that runs on the phone inside Plainpaper's own
camera, and an optional Quick scan that hands capture to the phone's built-in scanner.

## Clarifications

### Session 2026-10-24

- Q: Quick scan runs the phone maker's scanner (Google Play services on Android, Apple's on iOS),
  which may log usage on its own; is that acceptable? → A: Yes, on both platforms, opt-in and
  labelled as the phone's own scanner; Plainpaper adds no network permission; recorded as an
  accepted deviation in the plan's Constitution Check.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The outline finds the document in a cluttered scene (Priority: P1)

A person points Plainpaper's camera at a page or card in an everyday setting: held in one hand,
lying on a patterned floor or a desk the same colour as the paper, crossed by a cable, next to
other objects. Within a moment the outline sits on the document's four corners, follows it as the
phone moves, and stays calm. When the person holds still, the page is captured automatically and
the crop matches the outline.

**Why this priority**: finding the page is the first thing every scan depends on; with a wrong
outline nothing else in the app matters.

**Independent Test**: run the cluttered-scene photo set (hand-held card, cable across page, light
floor, busy desk) through detection and compare the corners with the marked ones; then on a phone
point the camera at a hand-held card and see the outline lock on and auto-capture fire.

**Acceptance Scenarios**:

1. **Given** a card held in a hand with fingers over one edge, **When** the camera points at it,
   **Then** the outline follows the card's four corners (the hidden part is completed) within
   one second.
2. **Given** a page with a cable lying across it, **When** the camera points at it, **Then** the
   outline is the page's outline, not a shape formed by the cable.
3. **Given** a white page on a light floor, **When** the camera points at it, **Then** the outline
   is found and the captured crop contains the whole page and nothing of the floor beyond a thin
   margin.
4. **Given** no document in view, **When** the camera points at a desk or a wall, **Then** no
   outline is shown and nothing is captured automatically.
5. **Given** the outline is on the document and Auto is on, **When** the person holds steady,
   **Then** the page is captured automatically and the review step from Feature 003 opens.

---

### User Story 2 - Quick scan with the phone's built-in scanner (Priority: P2)

A person who prefers their phone's own scanner taps Quick scan on the home screen (and beside
the shutter in the camera). The phone's built-in scanner opens; they scan one or more pages
there and confirm. The pages appear in Plainpaper's session, already straightened, where they can
pick a look, rotate, flip and continue like any other page.

**Why this priority**: it gives an immediately reliable path on phones that have a built-in
scanner while our own detection improves, at almost no cost.

**Independent Test**: on a phone with a built-in scanner, tap Quick scan, scan two pages, confirm,
and check that both pages appear in the session with the Enhanced look and can be rotated,
flipped and switched to Black & white.

**Acceptance Scenarios**:

1. **Given** a phone with a built-in document scanner, **When** the home screen or camera is
   shown, **Then** a Quick scan action is visible and says that it uses the phone's own scanner.
2. **Given** Quick scan is tapped, **When** the person scans pages and confirms, **Then** every
   page joins the capture session in order, in the Enhanced look, with Rotate, Flip and the looks
   available.
3. **Given** the person cancels the built-in scanner, **When** it closes, **Then** they are back
   where they started and nothing was added.
4. **Given** a phone without a built-in scanner (or where it cannot run), **When** the home
   screen or camera is shown, **Then** Quick scan is not offered.
5. **Given** the built-in scanner needs a one-time setup on first use, **When** Quick scan is
   tapped, **Then** the person sees a short message that the phone is preparing its scanner and
   can use Plainpaper's camera instead.

---

### User Story 3 - Detection keeps working on every phone (Priority: P3)

On a low-end phone, or if the document-finding model cannot be loaded, Plainpaper falls back to
the existing detector without errors; the person can still capture with the shutter and adjust
the result. On capable phones the model is used for both the live outline and the final crop.

**Why this priority**: the constitution requires graceful degradation on low-end phones.

**Independent Test**: force the fallback (low-end profile, model unavailable) and capture a page;
the camera works, the outline comes from the existing detector, and no error is shown.

**Acceptance Scenarios**:

1. **Given** a low-end phone, **When** the camera runs, **Then** the live outline still updates
   smoothly (model or fallback, whichever keeps it smooth on that phone).
2. **Given** the model is unavailable, **When** a page is captured or imported, **Then** the
   existing detector is used and the page is processed normally.

---

### User Story 4 - Adjust the border by hand (Priority: P2)

*Added 2026-10-25 at the owner's request.*

After a shot (in the review step) or later in the page viewer, the person taps Crop. The original
photo appears with the detected border; they drag any of the four corners (a magnifier shows the
area under the finger), can reset to the automatic border or select the whole photo, and tap
Apply. The page is re-rendered from the original with their border, keeping its look, rotation
and flip.

**Why this priority**: no detector is right every time; a quick manual fix saves a retake.

**Independent Test**: capture a page, open Crop, drag one corner inwards, Apply, and check the
page is re-rendered with that border; open Crop again and see the edited border; tap Auto to get
the detected border back.

**Acceptance Scenarios**:

1. **Given** the review step or the page viewer, **When** Crop is tapped, **Then** the original
   photo is shown with the page's current border and four draggable corners.
2. **Given** a corner is dragged, **When** the finger moves, **Then** the border follows, stays
   inside the photo, and a magnifier shows the area under the finger.
3. **Given** a drag would make the border cross itself or smaller than 5% of the photo, **When**
   the finger moves there, **Then** the border does not accept that position.
4. **Given** an edited border, **When** Apply is tapped, **Then** the page is re-rendered from the
   original with that border and keeps its look, rotation and flip; Cancel leaves it unchanged.
5. **Given** the editor, **When** Auto is tapped, **Then** the detected border returns; **When**
   Full photo is tapped, **Then** the border becomes the photo's edges.

### Edge Cases

- Two documents in view: the outline goes to the larger, more central one; it must not jump
  between them on every frame.
- Document partly outside the frame: the outline is shown on the visible part with the missing
  corners at the frame edge, and auto-capture does not fire.
- Very small document (business card far away, under 5% of the view): no outline; the hint asks
  to move closer.
- Document with a dark background (dark card, dark book cover): detected by shape, not only by
  being bright.
- Glare covering a corner: the outline still completes the corner from the visible sides.
- The built-in scanner returns pages at a different size or orientation than Plainpaper's camera:
  pages keep their returned orientation; rotation starts at 0.
- The built-in scanner is updated or removed by the system between sessions: Quick scan
  availability is checked each time it would be shown.
- Imported photos (Feature 003 import) use the same detection as captured photos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Detection MUST find the outline of a page, ID card or receipt covering 5–98% of the
  view in cluttered scenes: held in a hand with fingers over an edge, crossed by a cable, on a
  surface of similar colour, among other objects.
- **FR-002**: Detection MUST report a document's four corners even when one corner is hidden (by
  a finger, glare or the frame edge), completing it from the visible sides.
- **FR-003**: Detection MUST report "no document" for scenes without one (desk, wall, floor,
  screen showing no document) so that nothing is captured automatically.
- **FR-004**: The live outline MUST update at least 10 times per second on the reference
  mid-range phone and at least 5 times per second on a low-end phone, without flicker
  (Feature 003 FR-026 still applies).
- **FR-005**: Auto-capture MUST fire when the model-found outline is steady, using the existing
  steadiness rules; the final crop of the captured photo MUST use the same detection at full
  resolution.
- **FR-006**: The document-finding model MUST run entirely on the phone, MUST be owned by the
  project (no third-party weights whose licence is unclear), and MUST add no more than 5 MB to
  the installed app on each platform.
- **FR-007**: When the model is unavailable or the phone is too slow for it, detection MUST fall
  back to the existing detector without an error shown to the person.
- **FR-008**: The model's quality MUST be measured by the scan-quality suite on a fixed set of
  cluttered-scene photos with marked corners, and a change MUST NOT lower the measured results
  (constitution Principle VI).
- **FR-009**: Quick scan MUST open the phone's built-in document scanner and add the pages it
  returns to the capture session, in order, as new pages with the Enhanced look and with rotate,
  flip and looks available.
- **FR-010**: Quick scan MUST be offered only where a built-in scanner can run, MUST be labelled
  as using the phone's own scanner, and cancelling it MUST leave the session unchanged.
- **FR-011**: The app MUST NOT gain any network permission through this feature; permissions
  that libraries add only for their own usage logging MUST be removed, and the app MUST keep
  working with them removed.
- **FR-012**: Pages from Quick scan MUST NOT be cropped a second time; Plainpaper only applies the
  chosen look, rotation and flip to them.
- **FR-013**: All new text MUST be available in English and Arabic.
- **FR-014**: The review step and the page viewer MUST offer Crop: the original photo with the
  page's border, four draggable corners kept inside the photo, convex and at least 5% of it, a
  magnifier while dragging, Auto (detected border), Full photo, Cancel and Apply. Apply MUST
  re-render the page from the original with the edited border, keeping look, rotation and flip;
  the detected border is kept so Auto can restore it.

### Key Entities

- **Document detection**: four corners (or none), how sure the detector is, and which detector
  produced it (model or fallback).
- **Captured page** (from Feature 003): gains a source (Plainpaper camera, import, Quick scan) so
  that Quick scan pages are not cropped again.
- **Cluttered-scene photo set**: photos with marked corners (or marked "no document") used to
  measure detection quality.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the cluttered-scene photo set, at least 90% of documents are found with every
  corner within 2% of the image diagonal of its marked position.
- **SC-002**: On the photos without a document, a document is reported in at most 5% of them.
- **SC-003**: On the existing synthetic and reference sets, detection results are no worse than
  before this feature.
- **SC-004**: On the reference mid-range phone the outline appears on a hand-held card within
  1 second in 9 of 10 attempts, and auto-capture fires within 3 seconds of holding still.
- **SC-005**: The installed app grows by no more than 5 MB per platform.
- **SC-006**: A Quick scan of 3 pages, from tapping Quick scan to seeing them in the session,
  takes under 30 seconds for a person familiar with the phone's scanner.

## Assumptions

- The model is trained by the project on composites it generates: synthetic documents (the
  scan-quality suite's generator, ID-card-like and receipt-like layouts, Arabic and Latin text)
  placed on background photos and textures with permissive licences, with hands, cables, shadows,
  glare and blur added. A small set of the owner's real photos, kept locally, is used only to
  check results, never committed without the owner's consent.
- The model runs inside the existing scan engine, so Android and iOS behave the same.
- The built-in scanners are Google's document scanner on Android (through Google Play services)
  and Apple's document camera on iOS. On Android it may need a one-time download handled by the
  phone itself; Plainpaper does not download anything.
- The built-in scanner runs outside Plainpaper under the phone maker's own terms; Plainpaper
  receives only the finished page images. The owner accepted this (Clarifications 2026-10-24);
  the plan records it as a deviation in its Constitution Check.
- Review and edit of corners by hand (Feature "Review & edit") is a separate feature; this feature
  only improves automatic detection.
- The reference mid-range phone is the owner's phone used in Feature 003 testing; the low-end
  phone is the low-end Android emulator profile from Feature 002.
