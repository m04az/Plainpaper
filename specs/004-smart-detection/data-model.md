# Data model: Smart document detection

## CapturedPage (Feature 003, extended)

| Field | Type | Rule |
|-------|------|------|
| source | `PageSource` = Camera \| Import \| QuickScan | Set when the page is added; persisted as `source` in `session.json`; older indexes default to Camera (camera sessions) or Import (import sessions) from the session source. |

QuickScan pages: `corners` = full frame of the returned image, `confidence` = High, analysed with
`keepOrientation = true` (never re-cropped or turned automatically); `rotation`, `mirrored`,
`style`, `revision` work as for other pages.

## DetectionResult (engine, extended)

| Field | Type | Rule |
|-------|------|------|
| corners | Quad? | TL, TR, BR, BL in frame pixels; null when not found |
| confidence | High \| Low \| NotFound | Model: High = presence ≥ 0.8, mask/quad IoU ≥ 0.9, area ≥ 5%; Low = presence ≥ 0.5; else NotFound |
| detector | Model \| Classic | Which path produced the corners (diagnostics, scansuite) |
| presence | Float 0..1 | Model's "document present" score; 0 for Classic |

## DetectorModel

| Field | Type | Rule |
|-------|------|------|
| bytes | ONNX file | ≤ 3 MB; input 1×3×256×256 float RGB in 0..1; outputs `mask` 1×1×128×128 (logits) and `presence` 1×1 (logit) |
| sha256 | String | Recorded in the model card and checked by the scansuite |
| state (engine) | NotLoaded → Loaded \| Failed | Failed and NotLoaded use the classic detector |

## Detector policy (per engine)

`Model` when Loaded and (capability Full, or the median of the first 5 model passes ≤ 120 ms);
otherwise `Classic`. Re-evaluated when the engine is recreated.

## ClutteredSample (scansuite)

| Field | Type | Rule |
|-------|------|------|
| id | String | `cluttered-<kind>-<n>` |
| kind | hand \| cable \| similar-colour \| busy \| none | `none` = no document |
| corners | Quad? | marked corners; null for `none` |
| source | synthetic(seed) \| local photo | local photos are never committed |

Metrics: per sample corner error (max over corners, fraction of the diagonal), found/not-found;
set results: found rate with error ≤ 2% (SC-001), false-positive rate on `none` (SC-002).
