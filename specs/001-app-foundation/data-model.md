# Data Model: App Foundation

**Feature**: 001-app-foundation | **Date**: 2026-10-23

All types live in `:core:domain` (`app.plainpaper.core.domain.model`) unless stated otherwise.
Domain types have no Compose, Android or iOS imports.

## Domain entities

### AppLanguage

```kotlin
public enum class AppLanguage(public val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    ARABIC("ar");
}
```

- `tag` is the BCP 47 language tag applied to resources; `null` means follow the device.
- Validation: unknown persisted values map to `SYSTEM` (see contracts/preferences.md).

### ThemeMode

```kotlin
public enum class ThemeMode { SYSTEM, LIGHT, DARK }
```

- Unknown persisted values map to `SYSTEM`.

### AppPreferences

```kotlin
public data class AppPreferences(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
```

- Immutable value object; the single source of truth observed by the app shell and Settings.
- Default instance is what a fresh install sees.

### LayoutDirection (domain-level resolution)

```kotlin
public enum class AppLayoutDirection { LTR, RTL }

public fun resolveLayoutDirection(language: AppLanguage, systemLanguageTag: String?): AppLayoutDirection
```

- Rule: effective tag = `language.tag ?: systemLanguageTag`; RTL when the effective primary
  language subtag is `ar` (case-insensitive, prefix match on `ar-*`); otherwise LTR.
- Unsupported system languages (anything other than `en`/`ar`) fall back to English/LTR
  (FR-010); the resolver returns LTR and the resource system falls back to the default
  `values` folder.

## Repository interface (domain)

```kotlin
public interface PreferencesRepository {
    public val preferences: Flow<AppPreferences>
    public suspend fun setLanguage(language: AppLanguage)
    public suspend fun setThemeMode(mode: ThemeMode)
}
```

- `preferences` emits the current value immediately on collection and on every change.
- Writes are suspend functions and complete only after the value is durably stored.

## Use cases (domain)

| Use case | Signature | Behaviour |
|----------|-----------|-----------|
| `ObservePreferencesUseCase` | `operator fun invoke(): Flow<AppPreferences>` | Delegates to repository; `distinctUntilChanged()` |
| `SetLanguageUseCase` | `suspend operator fun invoke(language: AppLanguage)` | Delegates; no-op if unchanged |
| `SetThemeModeUseCase` | `suspend operator fun invoke(mode: ThemeMode)` | Delegates; no-op if unchanged |

## Coroutine contract (domain)

```kotlin
public interface DispatcherProvider {
    public val main: CoroutineDispatcher
    public val io: CoroutineDispatcher
    public val default: CoroutineDispatcher
}
```

## Persistence model (data)

See [contracts/preferences.md](contracts/preferences.md). Summary:

| Key | Type | Values | Default |
|-----|------|--------|---------|
| `language` | string | `system`, `en`, `ar` | `system` |
| `theme` | string | `system`, `light`, `dark` | `system` |

Mapper `PreferencesMapper` converts `Preferences` ↔ `AppPreferences`; unknown or missing keys
map to defaults and are never thrown.

## Presentation state

### Home

```kotlin
data class HomeUiState(
    val isEmpty: Boolean = true,          // always true in this feature; documents arrive in Feature 6
)

sealed interface HomeAction {
    data object ScanClicked : HomeAction
    data object ImportClicked : HomeAction
}

sealed interface HomeEffect {
    data object NavigateToScan : HomeEffect
    data object NavigateToImport : HomeEffect
}
```

### Settings

```kotlin
data class SettingsUiState(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val openSheet: SettingsSheet? = null,
    val appVersion: String = "",
)

enum class SettingsSheet { LANGUAGE, THEME }

sealed interface SettingsAction {
    data object LanguageRowClicked : SettingsAction
    data object ThemeRowClicked : SettingsAction
    data class LanguageSelected(val language: AppLanguage) : SettingsAction
    data class ThemeSelected(val mode: ThemeMode) : SettingsAction
    data object SheetDismissed : SettingsAction
}

sealed interface SettingsEffect   // none required in this feature; kept for UDF uniformity
```

### App shell

```kotlin
data class AppShellState(
    val language: AppLanguage,
    val themeMode: ThemeMode,
    val layoutDirection: AppLayoutDirection,
)
```

Derived in `:composeApp` from `ObservePreferencesUseCase` + system locale; drives
`AppEnvironment` (locale + layout direction) and `PlainpaperTheme` (dark flag).

## State transitions

```text
Settings: Idle --LanguageRowClicked--> Sheet(LANGUAGE) --LanguageSelected(x)--> persist(x) -> Idle
          Idle --ThemeRowClicked-----> Sheet(THEME)    --ThemeSelected(m)-----> persist(m) -> Idle
          Sheet(*) --SheetDismissed--> Idle
Preferences flow emits -> SettingsUiState.language/themeMode update -> AppShellState update
                       -> AppEnvironment re-keys content (locale) -> theme recomposes
```

## Navigation routes (serializable, `:composeApp`)

```kotlin
@Serializable sealed interface AppRoute
@Serializable data object Home : AppRoute
@Serializable data object Folders : AppRoute
@Serializable data object Settings : AppRoute
@Serializable data object ScanPlaceholder : AppRoute
@Serializable data object ImportPlaceholder : AppRoute
```

Tab destinations: Home, Folders, Settings (bottom bar visible). Placeholder destinations push on
top of the current tab (bottom bar hidden, back action returns).
