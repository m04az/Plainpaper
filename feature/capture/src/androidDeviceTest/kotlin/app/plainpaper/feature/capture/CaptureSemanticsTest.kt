package app.plainpaper.feature.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.plainpaper.core.capture.camera.CameraState
import app.plainpaper.core.designsystem.theme.PlainpaperTheme
import app.plainpaper.core.domain.model.capture.CameraPermissionState
import app.plainpaper.feature.capture.camera.CameraScreen
import app.plainpaper.feature.capture.camera.CameraUiState
import app.plainpaper.feature.capture.import.ImportItem
import app.plainpaper.feature.capture.import.ImportItemState
import app.plainpaper.feature.capture.import.ImportPhase
import app.plainpaper.feature.capture.import.ImportScreen
import app.plainpaper.feature.capture.import.ImportUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-010: every control on the camera and import screens has a label and a 48 dp target, the
 * hint pill is a live region, and the camera chrome mirrors in RTL (contracts/ui.md).
 */
@RunWith(AndroidJUnit4::class)
class CaptureSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val cameraState =
        CameraUiState(
            permission = CameraPermissionState.Granted,
            camera = CameraState.Running(hasFlash = true),
            pageCount = 2,
        )

    @Composable
    private fun Themed(
        direction: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit,
    ) {
        PlainpaperTheme(darkTheme = false, useArabicTypeface = direction == LayoutDirection.Rtl) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) { content() }
        }
    }

    private fun cameraScreen(direction: LayoutDirection) {
        compose.setContent {
            Themed(direction) {
                CameraScreen(state = cameraState, onAction = {}, preview = { modifier -> Box(modifier.fillMaxSize()) })
            }
        }
    }

    @Test
    fun cameraControlsAreLabelledAndLargeEnough() {
        cameraScreen(LayoutDirection.Ltr)
        listOf("Close camera", "Flash", "Automatic capture", "Scan mode", "Take photo", "2 pages").forEach { label ->
            compose
                .onNodeWithContentDescription(
                    label,
                ).assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithText("Done").assertIsDisplayed().assertHeightIsAtLeast(44.dp)
        compose.onNodeWithText("Hold steady — capturing…").assertIsDisplayed()
        val liveRegions =
            compose
                .onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
                ).fetchSemanticsNodes()
        assertTrue("hint pill must be a live region", liveRegions.isNotEmpty())
    }

    @Test
    fun cameraChromeMirrorsInRtl() {
        cameraScreen(LayoutDirection.Rtl)
        val root = compose.onRoot().getBoundsInRoot()
        val middle = (root.right - root.left) / 2
        val close = compose.onNodeWithContentDescription("Close camera").getBoundsInRoot()
        val done = compose.onNodeWithText("Done").getBoundsInRoot()
        assertTrue("close control sits at the end in RTL", close.left > middle)
        assertTrue("Done sits at the start in RTL", done.right < middle)
    }

    @Test
    fun importScreenLabelsTilesAndProgress() {
        val state =
            ImportUiState(
                phase = ImportPhase.Converting,
                items =
                    listOf(
                        ImportItem(0, "a", ImportItemState.Done("p1")),
                        ImportItem(1, "b", ImportItemState.Converting),
                        ImportItem(2, "c", ImportItemState.Skipped),
                        ImportItem(3, "d", ImportItemState.Queued),
                    ),
                estimatedSecondsLeft = 6,
            )
        compose.setContent { Themed { ImportScreen(state = state, onAction = {}) } }
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Converting 4 photos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Import progress").assertIsDisplayed()
        listOf("Converted", "Converting", "Skipped", "Waiting").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        compose.onNodeWithText("Processed on this phone. Your photos never leave it.").assertIsDisplayed()
    }
}
