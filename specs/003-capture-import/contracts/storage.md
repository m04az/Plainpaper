# Contract: capture session storage

Root: `<CAPTURE_ROOT>/` = Android `filesDir/capture`, iOS `Documents/capture` (excluded from
iCloud backup with `NSURLIsExcludedFromBackupKey`).

```text
capture/
└── <sessionId>/
    ├── session.json           # index, rewritten atomically (tmp + rename) on every change
    ├── original-000.jpg       # camera JPEG or copied import (EXIF kept; orientation honoured by the decoder)
    ├── page-000.jpg           # processed Scan-style page (grayscale JPEG q90) once Ready
    └── thumb-000.jpg          # 400 px long edge
```

`session.json` (kotlinx serialization, `schemaVersion` 1):

```json
{
  "schemaVersion": 1,
  "id": "9f1c…",
  "source": "Camera",
  "createdAtEpochMillis": 1792800000000,
  "status": "Active",
  "pages": [
    {
      "id": "p0", "position": 0,
      "originalPath": "original-000.jpg", "pagePath": "page-000.jpg", "thumbnailPath": "thumb-000.jpg",
      "state": {"type": "Ready"},
      "tag": {"type": "IdFront", "cardId": "c1"},
      "corners": [x0,y0,x1,y1,x2,y2,x3,y3], "confidence": "High",
      "pageSize": "Natural", "rotationApplied": 0
    }
  ]
}
```

Rules:
- Paths inside the JSON are relative to the session folder; the repository resolves them.
- Only one `Active`/`HandedOff` session exists at a time; `start()` discards any other
  `Active` session that has zero pages and leaves one with pages for recovery.
- A page whose `state` is `Processing` when the file is read is reported as `Pending`.
- `discard()` deletes the whole folder; failures to delete are logged, never thrown.
- Unknown fields are ignored (`ignoreUnknownKeys = true`) so Feature 4 can add fields.
