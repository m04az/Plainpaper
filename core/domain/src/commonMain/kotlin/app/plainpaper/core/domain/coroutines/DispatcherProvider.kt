package app.plainpaper.core.domain.coroutines

import kotlinx.coroutines.CoroutineDispatcher

/** Injected everywhere a dispatcher is needed so tests can substitute them (constitution V). */
public interface DispatcherProvider {
    public val main: CoroutineDispatcher
    public val io: CoroutineDispatcher
    public val default: CoroutineDispatcher
}
