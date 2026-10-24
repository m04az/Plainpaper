package app.plainpaper.core.domain.usecase.capture

import app.plainpaper.core.testing.FakeProStatusRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportLimitTest {
    @Test
    fun freeUsersAreLimitedToTen() {
        assertEquals(ImportLimit.Decision.Proceed(10), ImportLimit.evaluate(10, isPro = false))
        assertEquals(
            ImportLimit.Decision.ExceedsFreeLimit(limit = 10, picked = 11),
            ImportLimit.evaluate(11, isPro = false),
        )
        assertEquals(ImportLimit.Decision.Proceed(0), ImportLimit.evaluate(0, isPro = false))
    }

    @Test
    fun proHasNoLimit() {
        assertEquals(ImportLimit.Decision.Proceed(250), ImportLimit.evaluate(250, isPro = true))
    }

    @Test
    fun useCaseReadsTheProStatus() =
        runTest {
            val pro = FakeProStatusRepository(isPro = false)
            val useCase = EvaluateImportLimitUseCase(pro)
            assertEquals(ImportLimit.Decision.ExceedsFreeLimit(10, 12), useCase(12))
            pro.state.value = pro.state.value.copy(isPro = true)
            assertEquals(ImportLimit.Decision.Proceed(12), useCase(12))
        }
}
