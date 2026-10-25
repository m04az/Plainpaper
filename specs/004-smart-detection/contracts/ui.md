# UI contract

- **Home**: under "Scan a page" / "Import photos", a third action "Quick scan" with the subtitle
  "Uses your phone's own scanner" — shown only when `availability() != Unavailable`.
- **Camera**: a small "Quick scan" text button above the mode switcher (same visibility rule).
- **Tap Quick scan**: NeedsSetup → snackbar "Your phone is preparing its scanner. Try again in a
  moment, or use Plainpaper's camera." Ready → system scanner opens; on return with pages →
  a session (current camera session or a new one) gets the pages and the page viewer opens;
  Cancelled → no change; Failed → snackbar "The phone's scanner could not be opened."
- **Camera outline**: unchanged visuals; the hint uses the existing kinds. When the model reports
  presence < 0.2 for 3 s, hint = MoveCloser ("Point at a document").
- Strings (EN + AR): quickscan_action, quickscan_subtitle, quickscan_preparing,
  quickscan_failed.
