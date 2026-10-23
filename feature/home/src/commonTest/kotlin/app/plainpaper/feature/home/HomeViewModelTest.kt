package app.plainpaper.feature.home

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeViewModelTest {
    private val viewModel = HomeViewModel()

    @Test
    fun initialStateIsEmpty() {
        assertEquals(HomeUiState(isEmpty = true), viewModel.uiState.value)
    }

    @Test
    fun scanClickedEmitsNavigateToScan() =
        runTest {
            viewModel.effects.test {
                viewModel.onAction(HomeAction.ScanClicked)
                assertEquals(HomeEffect.NavigateToScan, awaitItem())
                expectNoEvents()
            }
            assertEquals(HomeUiState(), viewModel.uiState.value)
        }

    @Test
    fun importClickedEmitsNavigateToImport() =
        runTest {
            viewModel.effects.test {
                viewModel.onAction(HomeAction.ImportClicked)
                assertEquals(HomeEffect.NavigateToImport, awaitItem())
                expectNoEvents()
            }
            assertEquals(HomeUiState(), viewModel.uiState.value)
        }
}
