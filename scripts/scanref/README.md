# Reference-set tooling

Owner workflow for `reference-set/` (spec 002, research R13):

1. `pip install -r scripts/scanref/requirements.txt`
2. Photograph your own documents with your phone (identity documents: cover sensitive
   fields physically before photographing; never include anyone else's data).
3. Ingest: `python3 scripts/scanref/ingest.py ~/Downloads/IMG_1234.jpg --id receipt-ar-001 --tags receipt,arabic,hard-shadow --language ar`
   (applies EXIF rotation, strips all metadata including GPS, downsizes to ≤ 12 MP, JPEG q85,
   appends a manifest stub). Use `--rotate 90|180|270` to create deliberately rotated cases.
4. Annotate corners: `python3 scripts/scanref/annotate.py --all-missing` (click TL, TR, BR, BL).
5. For legibility scoring add `reference-set/transcripts/<id>.txt` (one physical line per
   line, reading order) and a clean reference image `reference-set/clean/<id>.png`
   (flatbed scan or flat, evenly lit photo of the same page), then set both paths in the
   manifest entry.
6. Competitor outputs: import the same photo from the gallery into Adobe Scan, Microsoft
   Lens and CamScanner (default document mode, best-quality export) and run
   `python3 scripts/scanref/ingest.py <export> --competitor adobe-scan --id receipt-ar-001`
   (stores a 150 DPI JPEG and links it in the manifest).

Budget: ≤ 150 MB of photos, ≤ 110 MB of competitor exports, plain git (no LFS).
