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

## sherpa-onnx — Apache License 2.0

Source: https://github.com/k2-fsa/sherpa-onnx

The offline keyword spotter used for the "小厨小厨" wake word is
`com.bihe0832.android:lib-sherpa-onnx:6.25.21` (a Maven Central redistribution of
sherpa-onnx's `com.k2fsa.sherpa.onnx` Kotlin API) with its `lib-onnx`
(`libonnxruntime.so`). Two models ship in `app/src/main/assets/`:

- `kws/` — Chinese keyword-spotting model (`sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01`,
  int8, about 5 MB) for the wake word;
- `asr/` — Chinese streaming recognizer (`sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23`,
  int8, about 24 MB) that shows words while the cook is talking and detects the end of a sentence;
- `models/sensevoice/` — SenseVoice model (`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17`,
  int8, about 228 MB) that re-reads the recorded clip and produces the final text.

The SenseVoice weights are **not** covered by Apache-2.0 or MIT: they follow the FunASR Model
Open Source License (https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE). The app is
for private use; distributing it, or shipping these weights inside another product, needs that
licence checked first. The model came from the sherpa-onnx release
`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2`; `model.int8.onnx` is
239,233,841 bytes (SHA-256 `C71F0CE00BEC95B07744E116345E33D8CBBE08CEF896382CF907BF4B51A2CD51`)
and `tokens.txt` is 315,894 bytes (SHA-256 `F449EB28DC567533D7FA59BE34E2ABCA8784F771850C78A47FB731A31429A1DC`).

Audio is processed inside the app process and is never uploaded; answers and questions are
exchanged as text. The wake word is off by default and only runs while the cooking screen is in
the foreground. Full Apache License 2.0 text is bundled as `MIHON-LICENSE.txt` (same licence) and
shown in the app's licences page.

## AI assistant — Zhipu (智谱)

The cooking assistant sends the current dish's title, ingredients, current step,
the user's stored taste tags and dish notes, and the last one or two question
turns to Zhipu's OpenAI-compatible endpoint (`open.bigmodel.cn`) and streams the
answer back. The detail page's assistant sends the dish's ingredient list,
warnings and this dish's notes with the typed question. When a new recipe is
prefilled and neither the bundled catalogue nor the pinned HowToCook page has
the dish, only the typed dish name is sent, and the reply is stored as an
editable draft clearly marked as AI-generated and untried. No image, audio or
contact data is sent. Speech recognition runs on-device with the bundled
sherpa-onnx models, and speech synthesis uses the device's own TTS; the
microphone is only open while the user is talking or the wake word is enabled.
This build embeds a personal API key for private use and is not meant for
distribution.



## Software dependencies

AndroidX (including Compose, Activity, Lifecycle, Navigation and DataStore), Kotlin,
kotlinx.coroutines, kotlinx.serialization, Coil, Material icons and the Gradle
wrapper use Apache License 2.0. Dependency versions are fixed in
`gradle/libs.versions.toml`. JUnit 4, used only in tests, uses Eclipse Public
License 1.0.

Permissions declared by the app and why:

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Look up public recipe pages and ask the AI assistant |
| `RECORD_AUDIO` | Tap-to-talk speech recognition and the optional wake word |
| `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `VIBRATE` | Cooking timers that keep ringing when the app is in the background |

The app does not bundle network image-loading components: covers are local assets or
files in the app's private storage. Gallery covers use Android's photo picker without
broad media storage permissions.

## Nutrition reference data

Nutrition estimates use selected records from Public Health England's CoFID 2021 (Open Government Licence v3.0) and USDA FoodData Central SR Legacy, April 2018 (public domain). Contains public sector information licensed under the Open Government Licence v3.0. Original food identifiers and source checksums are retained with the selected data.

- CoFID: https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid
- OGL: https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
- USDA: https://fdc.nal.usda.gov/download-datasets/
