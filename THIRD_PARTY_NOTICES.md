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

## FoodiesFeed photographs — CC0

Source: https://www.foodiesfeed.com · License: https://www.foodiesfeed.com/license

Twenty-two expansion recipes use FoodiesFeed photographs. FoodiesFeed releases its
photos under a license equivalent to Creative Commons Zero (CC0): they may be
copied, modified and redistributed, including commercially, and attribution is not
required. DeepCook records the source page for each image in
`app/src/main/assets/photo_sources.json` anyway (`cc0Expansion`), and crops each
photo to 4:3, resizes it to 1200×900 and re-encodes it as JPEG.

Only original photography from the site's legacy library is used. Files served
from FoodiesFeed's `/generated/` path are AI output and are deliberately excluded,
so no generated image ships in this app.

## Expansion recipe content

The expansion catalog (100 recipes: Chinese, Japanese, Korean, American, European,
Southeast Asian, and other cuisines such as Indian, Mexican, Middle Eastern, North
African and Turkish) is original DeepCook demonstration prose written after light
verification against public recipe references found with web search. No recipe text
is copied from those references. Where a single authoritative page was confirmed, its
URL is stored in that recipe's `source.url` field and linked from the detail page;
unconfirmed references are omitted rather than guessed. These 100 entries remain
separately labeled as project-authored demonstrations after the merge; their
unverified total-time and difficulty estimates are not retained as verified data.
The pinned input is preserved in `tools/cloud_recipes.json`. Expansion recipes either
ship with a bundled photograph (see the two sections above) or display the bundled
placeholder image when no clearly licensed, dish-accurate photograph was available.

## Software dependencies

AndroidX (including Compose, Activity, Lifecycle, Navigation and DataStore), Kotlin,
kotlinx.coroutines, kotlinx.serialization, Coil, Material icons and the Gradle
wrapper use Apache License 2.0. Dependency versions are fixed in
`gradle/libs.versions.toml`. JUnit 4, used only in tests, uses Eclipse Public
License 1.0. The app does not bundle network image-loading components or request
microphone or notification permissions. Internet and network-state permissions are
used only for optional public-page fallback when bundled lookup cannot fill a recipe.
Gallery covers use Android's photo picker without broad media storage permissions.
