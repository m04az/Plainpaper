# Contract: `reference-set/manifest.json` and `thresholds.json`

## manifest.json

```json
{
  "version": 1,
  "photos": [
    {
      "id": "receipt-ar-001",
      "file": "photos/receipt-ar-001.jpg",
      "tags": ["receipt", "arabic", "hard-shadow"],
      "language": "ar",
      "expectedCorners": [[412.0, 388.5], [2890.0, 401.0], [2871.0, 3920.0], [398.0, 3902.0]],
      "expectedRotation": 0,
      "transcript": "transcripts/receipt-ar-001.txt",
      "cleanReference": "clean/receipt-ar-001.png",
      "competitors": {
        "adobe-scan": "competitors/adobe-scan/receipt-ar-001.jpg",
        "microsoft-lens": "competitors/microsoft-lens/receipt-ar-001.jpg",
        "camscanner": "competitors/camscanner/receipt-ar-001.jpg"
      },
      "device": "Pixel 8", "notes": "thermal paper, window light from the left"
    }
  ]
}
```

Validation (suite fails fast with a message naming the entry):
- ids unique and match `^[a-z0-9-]+$`; every referenced file exists; photos ≤ 12 MP;
  `expectedCorners` is 4 points inside the photo in TL, TR, BR, BL order or `null` only when
  tags contain `no-page`; `transcript` ⇒ `cleanReference`; competitor keys ⊆
  {`adobe-scan`, `microsoft-lens`, `camscanner`}; competitor images ≥ 1000 px on the long
  edge (150 DPI exports).
- Minimum coverage for the gate to be meaningful (warning, not failure, until the set is
  complete): ≥ 3 photos per category listed in FR-015.
- The synthetic manifest (`build/scansuite/synthetic/manifest.json`, generated, same schema,
  every entry tagged `synthetic`) is always loaded in addition to `reference-set/manifest.json`;
  its thresholds (`synthetic*` keys in thresholds.json) always apply.

Transcript format: UTF-8 text, one physical text line per line, in reading order; the
scorer normalises NFC, strips Arabic diacritics (U+064B–U+0652, U+0670), collapses
whitespace and compares characters (Levenshtein).

## thresholds.json (`tools/scansuite/src/main/resources`)

```json
{
  "cornerErrorMax": 0.02,               "cornerWithinRate": 0.90,          // SC-001
  "noPageClassifiedRate": 1.00,                                           // SC-001
  "whitenessMin": 0.97,                 "textDarknessMax": 0.25,  "styleRate": 0.95,   // SC-002 (Scan style)
  "legibilityRelativeMin": 0.95,        "legibilitySubsetMin": 0.90,       // SC-003 (avg over ar/en/fr; per subset)
  "straightnessMaxDeg": 0.5,                                               // SC-004 (curled subset, Full)
  "orientationAccuracyMin": 0.95,                                          // FR-007
  "competitorMatchOrBeatRate": 0.60,    "competitorWorstRelative": -0.10,  // SC-008
  "cancelLatencyMaxMillis": 200,        "cancelTrials": 50,                // SC-007
  "hostMedianMillisInfoOnly": 2000,                                        // informational
  "syntheticCornerErrorMax": 0.01,      "syntheticCornerWithinRate": 1.00,  // exact ground truth
  "syntheticOrientationAccuracyMin": 1.00, "syntheticLegibilityRelativeMin": 0.95
}
```

A metric row is "matches or beats" a competitor when both `whiteness` and `legibility`
are ≥ the competitor's on that photo (legibility compared only when scored for both;
otherwise whiteness alone). The gate passes only when every non-informational threshold
holds; the report lists every failing row.

## report.json / report.md (`build/scansuite/`)

`report.json` serialises `QualityReport` (data-model.md). `report.md` is a human table:
aggregates vs thresholds with ✅/❌, per-category legibility, competitor summary table,
slowest 5 photos, and the list of failing rows. CI uploads both as artifacts and prints
the aggregate table in the job summary.
