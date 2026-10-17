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

## HowToCook recipes and photographs — Unlicense

Source: https://github.com/Anduin2017/HowToCook

372 Chinese recipe documents are normalized from revision
`743d7f46b7644c43673e0b2e255c745f6fa8ca5e`. The repository format template is excluded.
Source instructions, explicit ingredient quantities, and notes are retained;
Markdown formatting and contribution-request footers are removed. Recipe metadata
uses explicit source values; missing values are not fabricated. Introductory health
and nutrition claims are not imported into DeepCook's descriptions.

`app/src/main/assets/catalog_sources.json` records each recipe's original path,
fixed revision, URL, SHA-256, and corresponding bundled cover. Photographs are
oriented, resized and converted to JPEG. The complete upstream license is bundled
as `HOWTOCOOK-LICENSE.txt` and displayed in the app. The historical
`photo_sources.json` also documents the original demonstration photos retained for
the settings theme preview.

## Based Cooking recipes and photographs — Unlicense / public domain

Source: https://github.com/LukeSmithxyz/based.cooking

22 selected recipes are translated into Chinese from revision
`9d4a31a040eedd61e4fb608cb0c114ff9a7c4dd2`. The upstream README explicitly dedicates
the website and all its content, including submitted text and images, to the public
domain. The license is bundled as `BASED-COOKING-LICENSE.txt`. Recipe-level author
handles, fixed source URLs, and image provenance are retained. Translation inputs
are maintained in `tools/world_recipes_zh.json`.

DeepCook translates ingredient names, directions, descriptions, and notes without
inventing missing quantities or difficulty ratings. Source timing inconsistencies
are identified in translated notes; unreliable totals remain null. One source's
raw-meat washing instruction is adapted to cleaning and patting dry, explicitly
noted in that recipe. Both collections have not been independently kitchen-tested
by the DeepCook team; source and translation status are visible in the app.

## Software dependencies

AndroidX (including Compose, Activity, Lifecycle, Navigation and DataStore), Kotlin,
kotlinx.coroutines, kotlinx.serialization, Coil, Material icons and the Gradle
wrapper use Apache License 2.0. Dependency versions are fixed in
`gradle/libs.versions.toml`. JUnit 4, used only in tests, uses Eclipse Public
License 1.0. The app does not bundle network image-loading components or request
Internet, microphone or notification permissions.
