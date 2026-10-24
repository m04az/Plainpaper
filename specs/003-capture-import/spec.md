# Feature Specification: Capture & Import

**Feature Branch**: `003-capture-import`

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "Capture and import. Users can create a document either with the camera or by importing existing photos. Camera: live outline of the detected page, auto-capture when the page is steady, manual shutter, flash toggle, and multi-page capture in one session with a page counter. Import: pick many photos from the gallery at once (free users up to 10 per import, Pro unlimited) and convert them all into scanned pages in one go with progress shown and the ability to cancel. Camera permission is requested only when needed with a clear explanation; if denied, the user can still import photos. Modes on the camera: Document (default), ID card, Receipt."

## Overview

Capture & import is how pages get into Plainpaper. From Home, the person either opens the
camera and photographs one or many pages, or picks existing photos from their gallery. Every
photo is turned into a clean page by the scan engine (Feature 2) on the device, and the
resulting ordered set of pages is handed to the review step (Feature 4) as one capture
session. This feature delivers the camera screen, the import flow, the session that holds the
pages, and the permission handling. It does not edit pages, save documents or sell Pro.

The screens follow the approved design (artboards `Camera` and `Import progress` in the design
canvas linked from `specs/001-app-foundation/spec.md`).

## Clarifications

### Session 2026-10-23

- Q: Should the camera screen itself offer an "Import photos" entry, which the approved Camera artboard does not show? → A: No. Import is reached from Home and from the camera-permission denial screen only; the camera screen follows the approved design.
- Q: In this feature, what proportions do ID card pages get before Feature 7 composes front and back on one page? → A: Natural proportions (the card's own aspect), tagged as ID front/back so Feature 7 can place them at real size.
- Q: Should auto-capture fire when the engine's detection confidence is low (page edges partly outside the frame, open book, weak edges)? → A: No. Auto-capture requires high confidence; with low confidence the hint asks the person to adjust or tap the shutter, and the shutter always works.
- Q: Where does the default page size come from until the onboarding/localization feature exists? → A: From the device region using the scan engine's rule (Letter in US, Canada, Mexico, Philippines; A4 elsewhere); Receipt and ID card modes use Natural.
- Q: What happens to an import in progress when the app goes to the background? → A: It keeps converting for as long as the system allows, then pauses and resumes exactly where it stopped when the app returns; no progress is lost and no photo is converted twice.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Scan a page with the camera (Priority: P1)

A person taps "Scan" on Home. The camera opens full screen. As they point it at a page, a
blue outline hugs the page edges and follows it as the phone moves. A hint pill at the top
says what to do ("Hold steady — capturing…"). When the page is held still, the app takes the
photo by itself; the person can also tap the big shutter button at any time. The photo is
processed in the background into a clean page while the person is free to continue; a small
page thumbnail with a counter appears bottom-left. A flash toggle and an "Auto" toggle sit at
the top. Tapping "Done" hands the pages over to review.

**Why this priority**: This is the primary way documents are created and the action the
constitution requires to be one tap away from Home.

**Independent Test**: On a phone, tap Scan, point at a printed page on a desk, keep still,
and confirm the outline tracks the page, a capture happens without touching the shutter, the
counter reads 1, and Done opens the review hand-off showing a clean page.

**Acceptance Scenarios**:

1. **Given** the camera is open and a page is in view, **When** the phone moves, **Then** the
   outline moves with the page edges without visible lag and disappears when no page is found.
2. **Given** Auto is on and a page is in view with high detection confidence, **When** the
   outline stays steady for the steadiness period, **Then** one photo is taken, a brief confirmation flash is shown, the
   counter increments, and auto-capture does not fire again until the view changes
   meaningfully (a new page or a clearly moved page).
3. **Given** the camera is open, **When** the person taps the shutter, **Then** a photo is
   taken immediately whether or not a page outline is shown.
4. **Given** Auto is off, **When** a page is held steady, **Then** nothing is captured until
   the shutter is tapped, and the hint reads "Tap the shutter when ready".
5. **Given** the flash toggle is Off, **When** tapped, **Then** the flash turns on for the
   next captures and the control shows On; the choice is remembered for the next session.
6. **Given** a photo was taken, **When** processing finishes, **Then** the thumbnail shows the
   clean page; **When** processing fails for that photo, **Then** the thumbnail shows the
   original with a warning mark and the page is still part of the session.
7. **Given** one or more pages in the session, **When** the person taps Done, **Then** the
   session's pages, in capture order, are handed to the review step.

---

### User Story 2 - Import photos from the gallery (Priority: P2)

A person taps "Import photos" on Home. The system photo picker opens and they select several photos at once. The app shows an import screen
titled "From Photos" with a heading such as "Converting 12 photos", a progress bar, a grid of
tiles that turn into clean-page thumbnails one by one with a green tick, an estimate of the
time left, a reassurance that photos never leave the phone, and a Cancel action. When all
photos are converted, the heading changes to "12 pages ready" and a "Review 12 pages" button
leads to review.

**Why this priority**: Many documents already exist as photos (sent on WhatsApp, taken
earlier). Import is also the fallback when the camera permission is denied.

**Independent Test**: Pick 5 photos from the gallery and confirm the progress screen counts
through them, each tile gets a tick, the button becomes "Review 5 pages" and opens the review
hand-off with 5 clean pages in the picked order.

**Acceptance Scenarios**:

1. **Given** the picker returns N photos (N ≤ 10 or the user is Pro), **When** import starts,
   **Then** the screen shows "Converting N photos", progress "k of N done", and an updating
   time estimate until all N are done.
2. **Given** a free user, **When** they pick more than 10 photos, **Then** before converting
   the app explains that the free plan imports 10 photos at a time and offers "Import the
   first 10" and "See Pro"; nothing is converted until they choose, and no photo is dropped
   silently.
3. **Given** a conversion is in progress, **When** the person taps Cancel, **Then** work
   stops within one second and the person chooses between keeping the pages converted so far
   (continue to review) or discarding them (back to Home).
4. **Given** one photo in the batch is unreadable or is not a photo, **When** the batch runs,
   **Then** that tile shows a warning, the rest continue, and the final heading counts only
   the pages that were converted plus a note of how many were skipped.
5. **Given** a photo contains no detectable page, **When** converted, **Then** it still
   becomes a page (whole photo cleaned) marked for attention so review can fix the crop.
6. **Given** the conversion finished, **When** the person taps "Review N pages", **Then** the
   pages are handed to review in the order the photos were picked.

---

### User Story 3 - Capture many pages in one session (Priority: P3)

A person scans a 6-page contract. After each capture the counter goes up and the preview is
ready for the next page right away. The thumbnail bottom-left shows the latest page. They can
keep going until Done. If they close the camera with pages in the session, the app asks
before throwing them away. If a phone call or a switch to another app interrupts, the pages
captured so far are still there when they come back.

**Why this priority**: Multi-page documents are the norm for contracts, homework and forms;
losing pages after an interruption would be the worst experience in the app.

**Independent Test**: Capture 6 pages, send the app to the background and return, confirm
the counter still reads 6, tap close and confirm a discard confirmation appears, then tap Done
and confirm 6 pages reach review in order.

**Acceptance Scenarios**:

1. **Given** a capture just happened, **When** the confirmation flash ends (under one second),
   **Then** the live preview and outline are active again and the counter shows the new total.
2. **Given** pages in the session, **When** the person taps the close control, **Then** a
   confirmation offers "Discard N pages" or "Keep scanning"; with zero pages the camera simply
   closes.
3. **Given** pages in the session, **When** the app goes to the background and returns within
   the same app process, **Then** the session, its pages and their processing state are intact.
4. **Given** pages still being processed, **When** the person taps Done, **Then** the review
   hand-off opens immediately and the remaining pages finish processing there, each thumbnail
   updating when ready.
5. **Given** 30 pages in one session on the low-end reference device, **When** capturing
   continues, **Then** the camera stays responsive and no page is lost.

---

### User Story 4 - Choose a capture mode (Priority: P4)

At the bottom of the camera a segmented control offers Document (default), ID card and
Receipt. Document mode behaves as in User Story 1. ID card mode shows a card-shaped guide and
the hint "Front side of the card", then "Back side of the card" after the first capture, and
tags the two pages as the front and back of one card. Receipt mode shows the hint "Fit the
whole receipt" and keeps the receipt's natural proportions instead of placing it on an A4 or
Letter page. The chosen mode is remembered for the next session.

**Why this priority**: The mode choice is on the camera and must exist here; the composition
of both ID sides on one page and receipt export options arrive with Feature 7 and build on the
tags this feature records.

**Independent Test**: Switch to ID card, capture front and back, confirm both pages are
tagged as one card's sides; switch to Receipt, capture a till receipt and confirm the page
keeps a tall, narrow proportion.

**Acceptance Scenarios**:

1. **Given** the camera is open, **When** the person taps a mode, **Then** the control
   highlights it, the hint and guide change, and the mode applies to the next captures.
2. **Given** ID card mode, **When** the front is captured, **Then** the hint asks for the back
   and the next capture is tagged as the back of the same card; a third capture starts a new
   card.
3. **Given** Receipt mode, **When** a long receipt is captured, **Then** the processed page
   keeps the receipt's proportions (no white A4 margins) and is tagged as a receipt.
4. **Given** the person switches mode mid-session, **When** they capture, **Then** earlier
   pages keep their own mode tags.

---

### User Story 5 - Permissions asked only when needed, never a dead end (Priority: P5)

The first time the person taps Scan, the app shows a short explanation ("Plainpaper needs the
camera to scan pages. Photos stay on this phone.") and then the system permission prompt. If
they refuse, the camera screen shows a friendly message with two ways forward: "Import photos
instead" and "Open settings" to grant the permission later. Import never requires a permission
prompt because the system photo picker is used.

**Why this priority**: The constitution requires permissions to be requested only at the
moment of need with an explanation, and every flow to remain usable when denied.

**Independent Test**: On a fresh install, deny the camera permission and confirm the denial
screen offers import and settings; import 3 photos successfully without any prompt; grant the
permission in system settings and confirm the camera works on return.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** Home is shown, **Then** no permission prompt appears.
2. **Given** the person taps Scan for the first time, **When** the explanation is shown and
   they continue, **Then** the system camera prompt appears; **When** they grant it, **Then**
   the live preview starts without returning to Home.
3. **Given** the camera permission was denied, **When** the person taps Scan, **Then** the
   camera screen shows the denial message with "Import photos instead" and "Open settings"
   and no repeated system prompt when the system no longer allows one.
4. **Given** the permission was granted later in system settings, **When** the person returns
   to the app, **Then** the camera works without restarting the app.
5. **Given** any state, **When** the person imports photos, **Then** no permission prompt is
   shown.

---

### User Story 6 - Choose how each page looks (Priority: P2)

*Added 2026-10-24 after the first phone test: the owner found black-and-white output too bright
and detail-poor as the only result and asked for CamScanner-style choices after capture.*

After capture or import the person sees each page large, with a row of looks: Original (the
straightened photo), Enhanced (white paper, crisp and rich ink — the default), Grayscale and
Black & white. Tapping a look re-renders that page in a moment; "Apply to all pages" uses it for
the whole session. The choice is kept with the page.

**Why this priority**: the default look decides whether the first result feels like a scanner
app or a photocopy; different documents need different looks.

**Independent Test**: capture a page with coloured ink, open it, switch through the four looks,
apply one to all pages, and confirm each page is re-rendered with the same crop.

**Acceptance Scenarios**:

1. **Given** a new page, **When** it is processed, **Then** it uses the Enhanced look.
2. **Given** a processed page, **When** the person picks another look, **Then** the page is
   re-rendered from the original photo with the same corners and the choice is shown as selected.
3. **Given** several pages, **When** "Apply to all pages" is tapped, **Then** every page is
   re-rendered in that look.
4. **Given** handwriting in red or blue ink, **When** Black & white is chosen, **Then** the strokes
   are solid black and complete.

### User Story 7 - Review each page right after the shot (Priority: P2)

*Added 2026-10-24 after the second phone test: the owner wants a CamScanner-style flow — the
border found automatically, the shot growing into a large preview, a look chosen, then saved
before the next page — plus rotate and flip buttons.*

After every shot (automatic or manual) the captured page grows from the outline into a large
preview over the camera. Under it the person picks a look (Original, Enhanced, Grayscale,
Black & white), can rotate the page by 90° or flip it, can retake it, and taps Save. Save keeps
the page and returns to the live camera for the next one; Done finishes the session. The same
rotate and flip buttons are on the page viewer after the session.

**Why this priority**: a wrong crop, look or orientation is caught while the paper is still in
front of the camera, so the person never has to redo a whole session.

**Independent Test**: capture a page, watch it grow into the preview, switch to Black & white,
rotate it once and flip it, Save, capture a second page, and confirm the session holds two
pages with the first one rotated, flipped and in Black & white.

**Acceptance Scenarios**:

1. **Given** the camera sees a page, an ID card or a receipt filling at least 5% of the view,
   **When** it is held steady, **Then** an outline follows it and the page is captured
   automatically (Auto on).
2. **Given** a shot was taken, **When** the page has been processed, **Then** it animates from the
   outline position into a large preview with the look chips, Rotate, Flip, Retake and Save.
3. **Given** the review preview, **When** Rotate is tapped, **Then** the page turns 90° clockwise;
   **When** Flip is tapped, **Then** it is mirrored left to right; both are kept with the page.
4. **Given** the review preview, **When** Retake is tapped, **Then** the page is removed and the
   live camera returns; **When** Save is tapped, **Then** the live camera returns with the page
   counter increased.

### Edge Cases

- The device has no camera (some tablets, emulators): the Scan action goes straight to the
  import flow and explains why.
- Very low light: the hint says "Too dark — turn on the flash or add light" and auto-capture
  pauses; the shutter still works.
- No page found for several seconds with Auto on: the hint changes to "Move closer or tap the
  shutter"; the shutter captures the whole frame and the page is marked for attention.
- The page fills the whole frame: the outline is the frame edge, confidence is low so
  auto-capture does not fire, the hint asks to move back or tap the shutter, and a captured
  page is marked for attention in review.
- Two pages in view (open book): the outline follows the larger page with low confidence, so
  auto-capture waits and the hint asks the person to adjust the framing or tap the shutter.
- Engine reports "too dark" or fails on a captured photo: the page keeps the original photo,
  is marked for attention, and the session continues; nothing crashes.
- Storage nearly full: capture stops with a clear message before any photo is lost; pages
  already in the session remain.
- The app process is killed while a session exists (memory pressure, forced stop): on next
  launch the pages already stored for that session are offered for recovery on Home ("You
  have an unfinished scan of N pages") with Continue and Discard.
- Import picks include very large photos (over 12 megapixels) or HEIC on Android: they are
  converted like any other photo; a photo that cannot be decoded is skipped with a warning.
- Import picks include the same photo twice: both become pages; no de-duplication.
- The app goes to the background during an import: conversion continues while the system
  allows, then pauses and resumes from the same photo on return; the progress screen shows
  the correct count when it reappears.
- Device rotated to landscape: the camera screen stays portrait with controls fixed; the
  engine's orientation correction uprights the page.
- Low-end device: the live outline runs at a lower rate and auto-capture needs a slightly
  longer steady period rather than being disabled; the preview never stutters below the floor
  in SC-002.
- Right-to-left language: the layout mirrors (close control, counter and Done swap sides),
  the mode control order mirrors, numbers follow the locale.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Scan action on Home (primary button and floating Scan button) MUST open the
  camera screen; the "Import photos" action on Home MUST open the system photo picker. The
  camera screen itself has no import entry (approved design); import is also offered on the
  camera-permission denial screen (FR-018).
- **FR-002**: The camera screen MUST show a full-screen live preview with a page outline drawn
  over the detected page edges, updated continuously, and hidden when no page is detected.
- **FR-003**: With Auto on, the app MUST capture a photo automatically only when detection
  confidence is high and the detected page has been steady for the steadiness period (outline corners moving less than 1.5% of the
  frame diagonal for 0.7 s on the mid-range device, 1.0 s on low-end) and the page covers at
  least 20% of the frame; with low confidence the hint MUST ask the person to adjust or tap
  the shutter; after an automatic capture it MUST NOT capture again until the
  outline has moved by more than 10% of the diagonal or disappeared and reappeared.
- **FR-004**: The shutter button MUST capture immediately regardless of detection state.
- **FR-005**: The camera screen MUST offer a flash toggle (Off/On) and an Auto toggle, both
  with their state visible, and MUST remember both choices and the selected mode across
  sessions.
- **FR-006**: Captures MUST use the camera's full photo resolution up to 12 megapixels; the
  original photo MUST be stored for the session so later features can re-edit it.
- **FR-007**: Every captured or imported photo MUST be processed by the scan engine on the
  device, in the background, in the Scan style with the default page size (from the device
  region: Letter in US, Canada, Mexico and the Philippines, A4 elsewhere, until a settings
  default exists; Natural in Receipt and ID card modes), while the person continues capturing
  or waits on the import screen. Imported pages carry the Document mode tag.
- **FR-008**: The session MUST keep an ordered list of pages, each with its original photo,
  its processed page (when ready), detected corners and confidence, capture mode tag, source
  (camera or import), and processing state (pending, ready, needs attention, failed).
- **FR-009**: The camera screen MUST show the page counter and the latest page thumbnail once
  the session has a page, and a Done action that hands the whole session to review.
- **FR-010**: Closing the camera with pages in the session MUST ask for confirmation to
  discard them; with no pages it closes immediately.
- **FR-011**: A session MUST survive the app going to the background and returning, and its
  stored pages MUST be recoverable on the next launch if the process was killed.
- **FR-012**: The import flow MUST let the person pick many photos at once with the system
  picker, and MUST convert them in the picked order with a progress screen showing the count
  done, a progress bar, a time estimate, per-photo tiles that update as each finishes, and the
  reassurance that photos stay on the phone.
- **FR-013**: Free users MUST be limited to 10 photos per import; when more are picked the app
  MUST explain the limit before converting and offer to import the first 10 or to see Pro.
  Pro users MUST have no limit. Until the Pro purchase feature exists, every user is treated as
  free.
- **FR-014**: Import MUST be cancellable; cancellation MUST stop processing within one second
  and let the person keep the already-converted pages (continue to review) or discard them.
  When the app goes to the background, an import MUST keep running while the system allows,
  then pause and resume from the same photo on return without converting any photo twice.
- **FR-015**: An unreadable or non-image item in an import MUST be skipped with a visible
  warning without stopping the batch; a photo with no detectable page MUST still become a page
  marked for attention.
- **FR-016**: The camera MUST offer the modes Document (default), ID card and Receipt. ID card
  mode MUST guide front then back, tag the two captures as sides of one card and produce each
  side at natural (card) proportions. Receipt mode MUST produce pages at natural proportions
  and tag them as receipts. Mode tags MUST be part of each page.
- **FR-017**: The camera permission MUST be requested only when the person first opens the
  camera, preceded by an in-app explanation; the app MUST NOT ask on launch or on Home.
- **FR-018**: When the camera permission is denied, the camera screen MUST show an explanation
  with "Import photos instead" and "Open settings"; import MUST work fully without any
  permission prompt; the camera MUST recover automatically when the permission is granted
  later.
- **FR-019**: On a device without a camera the Scan action MUST lead to import with an
  explanation.
- **FR-020**: Low light and "no page found" states MUST be explained by the hint pill, and
  auto-capture MUST pause in low light.
- **FR-021**: All text MUST exist in English and Arabic, the layout MUST mirror in right-to-
  left languages, every control MUST have a screen-reader label and a touch target of at least
  48 dp, and the shutter, Done, close, flash, Auto and mode controls MUST be reachable with a
  screen reader.
- **FR-022**: All processing MUST happen on the device; the feature MUST add no network
  access and no new permission other than the camera.
- **FR-024**: Every page MUST record its look (Original, Enhanced, Grayscale, Black & white);
  new pages use Enhanced. The page view MUST let the person change the look of one page or of all
  pages; a change MUST re-render from the original photo with the page's stored corners.
- **FR-025**: Black & white and Grayscale MUST keep coloured ink (red, blue, green pen) as dark,
  complete strokes; Enhanced MUST keep paper white and ink colour visible and crisp.
- **FR-026**: Page detection MUST find a page whose outline is cut by the photo border, joined to
  an open book's other page or touched by another object, and the live outline MUST NOT flicker
  (it appears after two agreeing frames and survives a single missed frame).
- **FR-027**: Detection MUST find documents covering 5–20% of the view (ID cards, receipts) when
  their outline lies inside the view and they are clearly brighter than their surroundings, and
  auto-capture MUST also fire on such low-confidence detections after a longer steady time.
- **FR-028**: After each camera shot the app MUST show the processed page in a review preview
  (growing from the outline) with looks, Rotate 90°, Flip, Retake and Save; Save returns to the
  live camera.
- **FR-029**: Every page MUST record a rotation (0/90/180/270, clockwise) and a mirror flag set by
  the person; the rendered page MUST apply them, and changing them MUST re-render the page into
  new files. The page viewer MUST offer the same Rotate and Flip buttons.
- **FR-030**: When no page outline is found, the app MUST NOT turn the photo upside down on its own.
- **FR-023**: Capture, import, session and permission decisions MUST be covered by unit tests
  (steadiness decision, auto-capture gating, free-limit rule, batch progress and cancellation,
  session ordering and recovery, permission state machine, mode tagging).

### Key Entities

- **CaptureSession**: an ordered list of pages created in one camera or import run, with its
  source (camera or import), creation time and the mode in use; handed to review as a whole.
- **CapturedPage**: original photo reference, processed page reference (when ready), detected
  corners and confidence from the engine, mode tag (Document, ID front, ID back, Receipt),
  source, position in the session, and processing state (pending, ready, needs attention,
  failed with a reason).
- **CaptureSettings**: flash on/off, Auto on/off, selected mode; remembered between sessions.
- **ImportBatch**: the picked photos in order, the per-item state, counts done/skipped, the
  time estimate, and whether it was cancelled.
- **CameraPermissionState**: not asked, granted, denied, denied permanently (system will not
  prompt again), unavailable (no camera).
- **ProStatus**: whether the user has Pro; always "free" until Feature 9.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From tapping Scan on Home, the live preview with outline is visible within 1.5 s
  on the mid-range reference device and 3 s on the low-end device (permission already
  granted).
- **SC-002**: The outline updates at least 15 times per second with under 150 ms lag behind
  the page on the mid-range device, and at least 6 times per second on the low-end device.
- **SC-003**: With a printed page on a desk under normal room light, auto-capture fires within
  1 s of the page becoming steady in at least 90% of 20 trials, and zero automatic captures
  happen during 2 minutes with no page in view or with a page only partly in the frame.
- **SC-004**: After a capture, the preview is live again within 1 s, and in a 10-page session
  every page is processed within 5 s of the last capture on the mid-range device.
- **SC-005**: Importing 10 photos of 12 megapixels completes within 25 s on the mid-range
  device and 60 s on the low-end device, with the progress display updating at least once per
  second.
- **SC-006**: Cancelling an import stops processing within 1 s in 100% of attempts, and the
  kept-pages count matches the tiles that showed a tick.
- **SC-007**: With the camera permission denied, a person reaches a successful import in at
  most 2 taps from the denial screen; no flow in the feature is blocked by a denied permission.
- **SC-008**: Zero crashes and zero lost pages across 50 consecutive camera sessions of 5
  pages and 20 imports of 10 photos on the low-end reference device, including 10 forced
  background/foreground cycles.
- **SC-009**: A free user who picks more than 10 photos is informed in 100% of cases before
  conversion starts; no photo is ever dropped without the person being told.
- **SC-010**: Every control on the camera and import screens has a screen-reader label and a
  48 dp target, and the screens render correctly in Arabic (right-to-left) in light and dark
  themes, verified by automated UI checks of labels, sizes and mirroring plus reviewed
  screenshots attached to the pull request.

## Assumptions

- The review step is Feature 4. Until it ships, "Done" and "Review N pages" open a minimal
  hand-off screen that lists the session's processed pages as thumbnails with their state, so
  this feature is demonstrable on its own; Feature 4 replaces that screen.
- Documents are not saved to a library in this feature (Feature 4 saves, Feature 6 lists). A
  session's originals and processed pages live in the app's private storage until review
  saves or discards them; an unfinished session is offered for recovery once and discarded if
  the person declines.
- Pro status comes from a single read-only "is Pro" signal that always answers "free" until
  Feature 9; the free import limit is 10 photos per import, not per day.
- The live outline uses the scan engine's page detection on reduced-size preview frames; the
  full-resolution photo is detected again when processed. Reference devices are those of
  Feature 2 (mid-range: the owner's Android phone and iPhone; low-end: the 2 GB / 2-core
  emulator).
- Composing an ID card's front and back on one page and receipt fit-to-page export belong to
  Feature 7; this feature records the tags they need.
- The system photo picker is used on both platforms, so import needs no storage or photo
  permission; on Android 8–12 the system picker may be a document picker, which is acceptable.
- Until the onboarding/localization feature adds a page-size setting, the default page size
  follows the device region using the scan engine's rule.
- The camera screen is portrait only in this feature.
- The Pro sheet shown at the import limit is a short explanation with a "See Pro" action that
  opens the Pro placeholder until Feature 9 exists; it never appears unless the person picks
  more than the free limit.
