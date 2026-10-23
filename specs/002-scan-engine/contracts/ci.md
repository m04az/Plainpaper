# Contract: CI changes for the scan engine

## New job `scan-quality` (required check on `main`)

```yaml
scan-quality:
  name: scan-quality
  runs-on: ubuntu-24.04
  steps:
    - uses: actions/checkout@v7
      with: { fetch-depth: 1 }
    - uses: dorny/paths-filter@v3
      id: changes
      with:
        filters: |
          engine:
            - 'core/scanengine/**'
            - 'core/scanengine-android/**'
            - 'core/domain/**'
            - 'tools/scansuite/**'
            - 'reference-set/**'
            - 'scripts/build-opencv-host.sh'
            - '.github/workflows/pr.yml'
    - name: Install Tesseract
      if: steps.changes.outputs.engine == 'true'
      run: sudo apt-get update && sudo apt-get install -y tesseract-ocr tesseract-ocr-ara tesseract-ocr-fra
    - uses: actions/cache@v6              # host OpenCV build
      if: steps.changes.outputs.engine == 'true'
      with:
        path: ~/.cache/plainpaper/opencv-4.13.0-linux-x86_64
        key: opencv-host-4.13.0-${{ hashFiles('scripts/build-opencv-host.sh') }}
    - name: Build host OpenCV (cache miss only)
      if: steps.changes.outputs.engine == 'true'
      run: scripts/build-opencv-host.sh
    - uses: actions/setup-java@v6 / gradle/actions/setup-gradle@v6 (as other jobs)
    - name: Reference quality suite
      if: steps.changes.outputs.engine == 'true'
      env: { PP_REQUIRE_OCR: "1" }
      run: ./gradlew :tools:scansuite:test :tools:scansuite:run
    - name: Publish summary
      if: always() && steps.changes.outputs.engine == 'true'
      run: cat build/scansuite/report.md >> "$GITHUB_STEP_SUMMARY"
    - uses: actions/upload-artifact@v7 (report.md, report.json, outputs/ thumbnails)
    - name: Skipped
      if: steps.changes.outputs.engine != 'true'
      run: echo "No engine changes; quality suite skipped" >> "$GITHUB_STEP_SUMMARY"
```

## Existing jobs

- `build-android`: `actions/cache@v6` on `~/.cache/plainpaper/downloads` (OpenCV Android
  SDK zip, keyed `opencv-android-4.13.0`); NDK r30 via `sdkmanager` if absent on the
  runner; `assembleDebug` now compiles `:core:scanengine-android` for three ABIs.
- `test-android`: unchanged command; `:core:scanengine` `commonTest` runs via
  `testAndroidHostTest`; Kover merge adds `:core:scanengine` (exclusions in the Kover
  convention: `*.bridge.*`, `*Decoder*`, `*CapabilityProvider*`, `*Jni*`, `*Cinterop*`).
- `test-ios` / `build-ios`: cache `~/.cache/plainpaper/downloads` (xcframework zip);
  Gradle runs CMake for iOS targets before `cinterop`; `xcodebuild` links
  `opencv2.xcframework`. CMake from the runner (`/usr/local/bin/cmake`).
- Branch protection `required_status_checks.contexts` += `scan-quality`.
- `static-analysis`: detekt/Spotless cover `tools/scansuite` and the new modules; a new
  `scripts/check-native-format.sh` runs `clang-format --dry-run -Werror` on `native/`
  (clang-format 18 is preinstalled on the runner; locally via Homebrew `clang-format`).

## Local developer prerequisites (added to CLAUDE.md / README)

`brew install cmake ninja tesseract tesseract-lang clang-format`; Android NDK r30 via SDK
Manager; `pip install -r scripts/scanref/requirements.txt` for the ingest/annotation tools.
