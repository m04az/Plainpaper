package app.plainpaper.core.testing

import app.plainpaper.core.scanengine.ScanPhoto

/** A photo with dimensions only; the fakes never read pixels. */
class FakeScanPhoto(
    override val width: Int = 4000,
    override val height: Int = 3000,
) : ScanPhoto
