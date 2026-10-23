# Preferences Contract

**Feature**: 001-app-foundation | **Store**: Preferences DataStore (`androidx.datastore:datastore-preferences-core:1.3.0`)

## File

- Name: `plainpaper.preferences_pb`
- Android path: `context.filesDir.resolve("datastore/plainpaper.preferences_pb")`
- iOS path: `<NSDocumentDirectory>/datastore/plainpaper.preferences_pb`
- Provided via `expect fun preferencesDataStorePath(): String` in `:core:data`, created once
  by Koin as a singleton (`PreferenceDataStoreFactory.createWithPath`).

## Keys

| Key name | DataStore type | Allowed values | Default when missing/unknown |
|----------|----------------|----------------|------------------------------|
| `language` | `stringPreferencesKey` | `system`, `en`, `ar` | `system` |
| `theme` | `stringPreferencesKey` | `system`, `light`, `dark` | `system` |

## Mapping

```text
AppLanguage.SYSTEM  <-> "system"
AppLanguage.ENGLISH <-> "en"
AppLanguage.ARABIC  <-> "ar"
ThemeMode.SYSTEM    <-> "system"
ThemeMode.LIGHT     <-> "light"
ThemeMode.DARK      <-> "dark"
```

Rules:
- Reading an unrecognised string MUST yield the default and MUST NOT throw.
- Writing stores the lowercase token exactly as above.
- `preferences` flow catches `IOException` on read, emits `emptyPreferences()` (→ defaults), and
  rethrows anything else.
- Adding a key in a later feature does not require migration; removing or renaming one does
  (documented in that feature's plan).

## Test obligations (`:core:data` commonTest)

- Mapper: every enum value round-trips; unknown strings map to defaults; missing keys map to
  defaults.
- Repository: `setLanguage` then `preferences.first()` reflects the change; `setThemeMode`
  likewise; concurrent writes keep both keys (write language then theme, both persist).
  Tests use a temporary file path (`kotlinx.io` / platform temp dir) and a `TestScope`.
