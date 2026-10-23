# UI Contracts: App Foundation

**Feature**: 001-app-foundation | **Date**: 2026-10-23

These are the interfaces between modules. They are the surface that tests target; bodies are
implementation.

## 1. Navigation (`:composeApp`)

| Route | Shows bottom bar | Entered from | Back behaviour |
|-------|------------------|--------------|----------------|
| `Home` | yes (Documents selected) | start destination; Documents tab | exits app |
| `Folders` | yes (Folders selected) | Folders tab | pops to Home |
| `Settings` | yes (Settings selected) | Settings tab | pops to Home |
| `ScanPlaceholder` | no | Home "Scan a page", Scan FAB (any tab) | pops to previous |
| `ImportPlaceholder` | no | Home "Import photos" | pops to previous |

Rules:
- Tab taps use `popUpTo(Home) { saveState = true }; launchSingleTop = true; restoreState = true`.
- Scan FAB is visible on all three tab destinations and always navigates to `ScanPlaceholder`.
- Placeholder screens render a top bar with a back arrow (mirrored in RTL), a title, a
  one-line body, and nothing else.

## 2. ViewModel contracts

Every ViewModel follows:

```kotlin
val uiState: StateFlow<UiState>          // started WhileSubscribed(5_000), initial = UiState()
fun onAction(action: Action)             // the only public entry point
val effects: Flow<Effect>                // Channel(BUFFERED).receiveAsFlow()
```

### HomeViewModel (`:feature:home`)

| Action | State change | Effect |
|--------|--------------|--------|
| `ScanClicked` | none | `NavigateToScan` |
| `ImportClicked` | none | `NavigateToImport` |

Constructor: `HomeViewModel()` (no dependencies in this feature; documents arrive later).

### SettingsViewModel (`:feature:settings`)

Constructor: `SettingsViewModel(observePreferences, setLanguage, setThemeMode, dispatchers, appVersion: String)`

| Action | State change | Side effect |
|--------|--------------|-------------|
| init | collects `observePreferences()` → `language`, `themeMode` | — |
| `LanguageRowClicked` | `openSheet = LANGUAGE` | — |
| `ThemeRowClicked` | `openSheet = THEME` | — |
| `LanguageSelected(l)` | `openSheet = null` | `setLanguage(l)` on `dispatchers.io` |
| `ThemeSelected(m)` | `openSheet = null` | `setThemeMode(m)` on `dispatchers.io` |
| `SheetDismissed` | `openSheet = null` | — |

Test obligations (Turbine): initial state equals defaults; state reflects repository emissions;
each action produces exactly the listed transitions; selections call the use case once.

## 3. Screen composables

All screens are stateless and take `(state, onAction)`; a `*Route` composable binds the
ViewModel via `koinViewModel()` and collects effects with `LaunchedEffect`.

```kotlin
@Composable fun HomeRoute(onNavigateToScan: () -> Unit, onNavigateToImport: () -> Unit)
@Composable fun HomeScreen(state: HomeUiState, onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier)

@Composable fun SettingsRoute()
@Composable fun SettingsScreen(state: SettingsUiState, onAction: (SettingsAction) -> Unit, modifier: Modifier = Modifier)

@Composable fun PlaceholderScreen(title: String, body: String, onBack: () -> Unit)
```

Accessibility contract: every icon-only control has `contentDescription`; row controls use
`Role.Button`; sheets use `ModalBottomSheet` with selectable rows (`Role.RadioButton`).

## 4. Design system (`:core:designsystem`)

### Theme

```kotlin
@Composable fun PlainpaperTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)
object PlainpaperTheme { val colors: ColorScheme; val typography: Typography; val spacing: Spacing; val shapes: PpShapes }
```

Tokens (from the approved design, board "Design tokens & components"):

| Token | Light | Dark |
|-------|-------|------|
| background | #F2F1EE | #0E0F12 |
| surface | #FFFFFF | #1C1D21 |
| onSurface (ink) | #111214 | #F2F1EE |
| onSurfaceVariant | #5E5E63 | #A3A29D |
| primary (ink blue) | #2340C8 | #8FA4FF |
| primaryContainer | #E8ECFB | #1A2447 (derived) |
| onPrimary | #FFFFFF | #0E0F12 |
| outline | #E2E0DA | #2C2D32 |
| success | #1C7C4A | #4CC38A (derived) |
| error | #B42318 | #F97066 (derived) |

Typography (Inter for Latin, IBM Plex Sans Arabic as fallback in the same `FontFamily`):

| Style | Size / weight |
|-------|---------------|
| largeTitle (displaySmall) | 34sp / 800 |
| title (headlineMedium) | 28sp / 800 |
| title2 (titleLarge) | 22sp / 700 |
| headline (titleMedium) | 17sp / 700 |
| body (bodyLarge) | 16sp / 400 |
| caption (bodySmall) | 13sp / 500 |

Spacing: `xs 4, s 8, m 12, l 16, xl 20, xxl 24, xxxl 32` dp. Shapes: `search 12`, `card 20`,
`toolbar 30`, `sheet 38`, `capsule = CircleShape`.

### Components

```kotlin
@Composable fun PpPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leadingIcon: ImageVector? = null, enabled: Boolean = true)
    // 52dp high capsule, primary bg, onPrimary text 17sp/600, shadow
@Composable fun PpGlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leadingIcon: ImageVector? = null)
    // 52dp capsule, translucent surface (62% white / 62% surface in dark), 1dp outline
@Composable fun PpFloatingTabBar(items: List<PpTabItem>, selected: PpTabItem, onSelect: (PpTabItem) -> Unit, modifier: Modifier = Modifier)
    // 62dp capsule, glass material; selected item tinted primary with primaryContainer pill
data class PpTabItem(val id: String, val label: String, val icon: ImageVector)
@Composable fun PpScanFab(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier)
    // 62dp circle, primary bg, scan-frame icon
@Composable fun PpEmptyState(title: String, body: String, illustration: @Composable () -> Unit, primary: @Composable () -> Unit, secondary: @Composable () -> Unit)
@Composable fun PpSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit)
    // uppercase 13sp caption + surface card with 20dp radius
@Composable fun PpSettingsRow(title: String, value: String?, icon: ImageVector, iconTint: Color, onClick: (() -> Unit)?, modifier: Modifier = Modifier)
    // min height 50dp (≥48dp touch target), trailing value + chevron (auto-mirrored)
@Composable fun PpTopTitle(text: String)   // 34sp/800 large title
```

RTL contract: components use `start`/`end` padding and `Icons.AutoMirrored` chevrons; no
`left`/`right` modifiers. `PpFloatingTabBar` item order follows layout direction automatically
because it is a `Row`.

## 5. Locale environment (`:composeApp`)

```kotlin
expect object LocalAppLocale {
    val current: String  @Composable get
    @Composable infix fun provides(value: String?): ProvidedValue<*>
}

@Composable fun AppEnvironment(language: AppLanguage, layoutDirection: AppLayoutDirection, content: @Composable () -> Unit)
    // CompositionLocalProvider(LocalAppLocale provides language.tag, LocalLayoutDirection provides dir) { key(language) { content() } }
```

Behavioural contract: changing `language` re-keys `content` so every `stringResource` re-reads
with the new locale; `LocalLayoutDirection` is always set from `layoutDirection`, never from
the platform. The `NavHostController` MUST be created with `rememberNavController()` in
`PlainpaperApp` **outside** `AppEnvironment` and passed in, so re-keying never resets the back
stack (spec edge case: switching language on a placeholder keeps the user there). The Android
`actual` captures the device locale once at first composition and restores it when the tag is
`null` ("Follow system").
