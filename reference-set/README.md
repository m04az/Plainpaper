# Reference set

Real photos that gate every change to the scan engine (spec 002, FR-015/FR-016; research R13).
The suite in `tools/scansuite` runs them, plus a generated synthetic set, on every pull request.

## Layout

| Path | Content |
|------|---------|
| `manifest.json` | One entry per photo: id, file, tags, language, expected corners, rotation, transcript, clean reference, competitor outputs (schema: `specs/002-scan-engine/contracts/reference-manifest.md`) |
| `photos/<id>.jpg` | ≤ 12 MP, JPEG q85, EXIF baked in, all metadata stripped |
| `clean/<id>.png` | Clean reference (flatbed scan or flat, evenly lit photo) for every transcribed photo |
| `transcripts/<id>.txt` | Ground-truth text, one physical line per line |
| `competitors/<app>/<id>.jpg` | 150 DPI exports from Adobe Scan, Microsoft Lens, CamScanner |
| `benchmarks/` | Device timing results (`<device>-<date>.json`) |

## Categories (tags)

`id-card`, `receipt`, `curled`, `folded`, `dark-room`, `hard-shadow`, `low-contrast-background`,
`arabic`, `english`, `french`, `mixed-script`, `stamp-signature`, `no-page`, `rotated`, `pencil`,
`inverted`, `flash-highlight`, `open-book`, `page-fills-frame`, `synthetic` (generated only).
Real-photo thresholds are enforced per category once it has at least three photos.

## Privacy and size

Only the owner's own documents; identity documents with sensitive fields physically covered
before photographing; nobody else's data. `scripts/scanref/ingest.py` strips all metadata
(including GPS). Budget: ≤ 150 MB of photos and ≤ 110 MB of competitor exports in plain git.

See `scripts/scanref/README.md` for the ingest, annotation and competitor-export workflow.
