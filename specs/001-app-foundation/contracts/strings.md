# String Resources Contract

**Feature**: 001-app-foundation | **Mechanism**: Compose Multiplatform resources
(`composeResources/values/strings.xml`, `composeResources/values-ar/strings.xml`)

Rules: no user-visible literal in Kotlin code; every key below exists in both locales; Arabic
uses Arabic-Indic punctuation where natural; placeholders use `%1$s`.

## `:composeApp` (shell)

| Key | en | ar |
|-----|----|----|
| `app_name` | Plainpaper | Plainpaper |
| `tab_documents` | Documents | المستندات |
| `tab_folders` | Folders | المجلدات |
| `tab_settings` | Settings | الإعدادات |
| `scan_fab_cd` | Scan | مسح مستند |
| `back_cd` | Back | رجوع |
| `placeholder_scan_title` | Scan a page | مسح صفحة |
| `placeholder_scan_body` | The camera scanner is coming in the next update. | ماسح الكاميرا قادم في التحديث التالي. |
| `placeholder_import_title` | Import photos | استيراد صور |
| `placeholder_import_body` | Importing photos from your gallery is coming soon. | استيراد الصور من المعرض قادم قريبًا. |
| `placeholder_folders_title` | Folders | المجلدات |
| `placeholder_folders_body` | Folders will arrive together with the document library. | ستتوفر المجلدات مع مكتبة المستندات. |

## `:feature:home`

| Key | en | ar |
|-----|----|----|
| `home_title` | Documents | المستندات |
| `home_empty_title` | No documents yet | لا توجد مستندات بعد |
| `home_empty_body` | Scan a page with your camera, or turn photos you already have into clean scans. | صوّر صفحة بالكاميرا، أو حوّل الصور الموجودة لديك إلى مستندات ممسوحة نظيفة. |
| `home_scan_button` | Scan a page | مسح صفحة |
| `home_import_button` | Import photos | استيراد صور |
| `home_empty_illustration_cd` | Illustration of a blank page | رسم توضيحي لصفحة فارغة |

## `:feature:settings`

| Key | en | ar |
|-----|----|----|
| `settings_title` | Settings | الإعدادات |
| `settings_group_app` | App | التطبيق |
| `settings_group_about` | About | حول |
| `settings_language` | Language | اللغة |
| `settings_appearance` | Appearance | المظهر |
| `settings_privacy` | Privacy | الخصوصية |
| `settings_privacy_value` | On-device only | على الجهاز فقط |
| `settings_version` | Version | الإصدار |
| `language_system` | Follow system | حسب النظام |
| `language_en` | English | English |
| `language_ar` | العربية | العربية |
| `theme_system` | System | النظام |
| `theme_light` | Light | فاتح |
| `theme_dark` | Dark | داكن |
| `sheet_language_title` | Language | اللغة |
| `sheet_theme_title` | Appearance | المظهر |
| `selected_cd` | Selected | محدد |

## Android launcher label

`androidApp/src/main/res/values/strings.xml` and `values-ar/strings.xml` carry `app_name`
only (the launcher cannot read Compose resources). iOS: `CFBundleDisplayName` = Plainpaper,
with `ar.lproj/InfoPlist.strings` identical (brand name is not translated).
