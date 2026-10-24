# Contract: screens, strings, accessibility (003)

Follows the `Camera` and `Import progress` artboards of the approved design.

## Camera screen (`CameraRoute`)

Layers, top to bottom: `CameraPreview` (aspect-fill), outline overlay (`PpOutline`: 3 dp
primary stroke, 14 dp corner dots, 14 % primary fill; hidden when `outline == null`; in
`Cooldown`/`Capture` flashes white for 300 ms), top bar (close glass button 44 dp; glass
pill with Flash toggle "Off/On" and "Auto" toggle), hint pill (centred, green dot when
`Tracking`, amber when `TooDark`/`LowConfidence`), bottom: `PpSegmentedControl` (Document,
ID card, Receipt), row with page thumbnail + `PpPageCounter` badge (hidden when 0),
`PpShutterButton` 78 dp, "Done" primary pill (hidden when 0 pages). Permission states
replace the preview with the explanation (`NotAsked`) or denial (`Denied*`) panel:
title, body, primary "Allow camera" / "Open settings", secondary "Import photos instead".
`Unavailable` navigates to Import with a one-line notice.

Hints (string keys): `camera_hint_hold_steady` ("Hold steady — capturing…"),
`camera_hint_tap_shutter`, `camera_hint_move_closer` ("Move closer or tap the shutter"),
`camera_hint_too_dark` ("Too dark — turn on the flash or add light"),
`camera_hint_adjust` ("Adjust the framing or tap the shutter"), `camera_hint_id_front` ("Front side of the card"),
`camera_hint_id_back` ("Back side of the card"), `camera_hint_receipt` ("Fit the whole receipt").

Discard confirmation: "Discard %1$d pages?" / "Keep scanning" / "Discard".
Storage full: "Not enough space to save more pages" with "Done" only.

## Import screen (`ImportRoute`)

Opens the picker on entry; cancelling the picker pops back. States: `LimitSheet`
("Free imports 10 photos at a time", body, "Import the first 10", "See Pro" → Pro
placeholder route in composeApp), `Converting` (title "From Photos", heading "Converting
%1$d photos", sub "%1$d of %2$d done · about %3$d s left", progress bar with
`progressBarRangeInfo`, 4-column tile grid: queued tile shows the original thumbnail tilted,
active tile has a 2.5 dp primary ring, done tile shows the page thumbnail with a green tick,
skipped tile shows a warning glyph; footer privacy card "Processed on this phone. Your photos
never leave it."; button "Converting…" disabled), `CancelChoice` sheet ("Keep %1$d converted
pages" / "Discard"), `Finished` (heading "%1$d pages ready", sub "All photos are now clean
scans." or "%1$d skipped", button "Review %1$d pages").

## Hand-off screen (`HandoffRoute`, placeholder until Feature 4)

Title "Pages" with count, grid of thumbnails with state badges (spinner for
Pending/Processing, warning for NeedsAttention/Failed), "Back to Home" primary. Marks the
session `HandedOff` on entry.

## Home additions

Recovery card above the list when a recoverable session exists: "You have an unfinished
scan of %1$d pages" with "Continue" (→ `HandoffRoute`) and "Discard".

## Accessibility and RTL

- Every control: `contentDescription`/`Role`, min 48 dp touch target (shutter 78 dp, glass
  buttons 44 dp visual with 48 dp touch via `minimumInteractiveComponentSize`).
- Shutter announces "Take photo"; counter announces "%1$d pages"; outline is decorative
  (`invisibleToUser`); hint pill is a `liveRegion` (polite).
- RTL: layout mirrors through `LocalLayoutDirection` (close control moves to the end,
  Done to the start); the mode control keeps logical order (first item at the start);
  numbers formatted with the active locale.
- Dark theme: the camera screen is always dark; the import screen uses theme tokens.
