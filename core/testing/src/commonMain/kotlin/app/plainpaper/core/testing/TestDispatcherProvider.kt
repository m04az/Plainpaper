package app.plainpaper.core.testing

import app.plainpaper.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Routes every dispatcher to one [TestDispatcher]. */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}
