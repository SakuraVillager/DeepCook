# Third-party notices

## Mihon — Apache License 2.0

Source: https://github.com/mihonapp/mihon

The settings row layouts, grouping, and theme preview presentation in
`core/designsystem/Preferences.kt` and `feature/settings/SettingsScreens.kt`
are adapted from Mihon's BasePreferenceWidget, TextPreferenceWidget,
SwitchPreferenceWidget, PreferenceGroupHeader, and AppThemePreferenceWidget.
DeepCook modifications replace application dependencies, resources, state access,
themes, previews, and content, and add switch semantics and single-choice modes.

Navigation motion is also adapted from Mihon's HomeScreen.kt and
presentation/util/Navigator.kt: sibling tabs use Fade Through (200ms,
initialScale=1f), and hierarchical navigation uses Shared Axis X (the library's
300ms/30dp defaults), reversed on pop. DeepCook retains AndroidX Navigation
Compose and separates the home tab container from the outer page navigator.

The complete upstream license is bundled at `app/src/main/assets/MIHON-LICENSE.txt`
and displayed in the app's open-source licenses page.

## Material Motion Compose — Apache License 2.0

Source: https://github.com/fornewid/material-motion-compose

DeepCook uses the same `io.github.fornewid:material-motion-compose-core:2.0.1`
dependency as Mihon, without modifying its easing, fade thresholds, scale or
translation implementations. Copyright 2021 SOUP. The complete Apache 2.0
license is bundled as `MIHON-LICENSE.txt` and displayed in the app's licenses page.

## HowToCook photographs — Unlicense

Source: https://github.com/Anduin2017/HowToCook

Bundled photographs are pinned to the revision recorded in
`app/src/main/assets/photo_sources.json`. That manifest records the original path,
URL, local asset, license, and transformations for every image. Photographs have
been oriented, resized, and converted to JPEG. The complete upstream license is
bundled as `HOWTOCOOK-LICENSE.txt` and displayed in the app.

Recipe prose is original DeepCook demonstration content, not a transcription of
the repository's recipe instructions. Sample content has not been independently
kitchen-tested, and the UI identifies it as sample content.

## Expansion recipe content

The expansion catalog (100 recipes: Chinese, Japanese, Korean, American, European,
Southeast Asian, and other cuisines such as Indian, Mexican, Middle Eastern, North
African and Turkish) is original DeepCook demonstration prose written after light
verification against public recipe references found with web search. No recipe text
is copied from those references. Where a single authoritative page was confirmed, its
URL is stored in that recipe's `source.url` field and linked from the detail page;
unconfirmed references are omitted rather than guessed. Expansion recipes ship
without photographs and display the bundled placeholder image.

## Software dependencies

AndroidX (including Compose, Activity, Lifecycle, Navigation and DataStore), Kotlin,
kotlinx.coroutines, kotlinx.serialization, Coil, Material icons and the Gradle
wrapper use Apache License 2.0. Dependency versions are fixed in
`gradle/libs.versions.toml`. JUnit 4, used only in tests, uses Eclipse Public
License 1.0. The app does not bundle network image-loading components or request
Internet, microphone or notification permissions.
