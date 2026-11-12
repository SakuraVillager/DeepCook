package com.deepcook.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepcook.app.core.designsystem.DeepCookTheme
import com.deepcook.app.core.designsystem.FavoriteGlyph
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class FavoriteAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private val backdrop = Color(0xFFA4EEF2)
    private val ink = Color(0xFF805600)

    @Test fun actualArtworkAlignsInkAndKeepsBothStatesTransparentAndConnected() {
        compose.setContent {
            DeepCookTheme(
                com.deepcook.app.core.model.UserPreferences(themeMode = com.deepcook.app.core.model.ThemeMode.LIGHT),
            ) {
                Column(Modifier.background(backdrop).padding(8.dp).testTag("favorite-proof")) {
                    Text("App 实际收藏组件", style = MaterialTheme.typography.titleLarge)
                    Column {
                        Specimen(false, true)
                        Specimen(true, true)
                    }
                    Row {
                        Specimen(false, false)
                        Specimen(true, false)
                    }
                }
            }
        }
        for (large in listOf(true, false)) {
            for (selected in listOf(false, true)) {
                val image = compose.onNodeWithTag("glyph-$selected-$large").captureToImage()
                val pixels = image.toPixelMap()
                val scale = (if (large) 144f else 24f) * image.width / (if (large) 256f else 176f) / 24f
                fun isInk(x: Int, y: Int): Boolean {
                    val c = pixels[x, y]
                    return abs(c.red - ink.red) + abs(c.green - ink.green) + abs(c.blue - ink.blue) < .12f
                }
                fun firstInk(left: Float, right: Float): Int = (0 until (7 * scale).roundToInt()).first { y ->
                    ((left * scale).roundToInt() until (right * scale).roundToInt()).any { x -> isInk(x, y) }
                }
                val starTop = firstInk(10f, 13f)
                val numberTop = firstInk(15.1f, 26f)
                assertTrue("Visible tops must align: $starTop vs $numberTop", abs(starTop - numberTop) <= 2)
                assertTrue("Top vertex must stay connected", isInk((12 * scale).roundToInt(), (2 * scale).roundToInt()))
                assertTrue("Right vertex must remain intact", isInk((21.7f * scale).roundToInt(), (9.4f * scale).roundToInt()))
                assertEquals(
                    "Only selected stars are filled",
                    selected,
                    isInk((12 * scale).roundToInt(), (12 * scale).roundToInt()),
                )
                for (y in 0 until (8 * scale).roundToInt()) {
                    for (x in (14 * scale).roundToInt() until (26 * scale).roundToInt()) {
                        assertTrue(
                            "Counter must reveal the backdrop, without a white patch",
                            pixels[x, y].red <= backdrop.red + .03f,
                        )
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = compose.onNodeWithTag("favorite-proof").captureToImage().asAndroidBitmap()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "DeepCook-favorite-actual-component.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DeepCook")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)!!.use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            resolver.update(
                uri,
                android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                },
                null,
                null,
            )
        }
    }

    @Composable
    private fun Specimen(selected: Boolean, large: Boolean) {
        Column {
            if (large) Text(if (selected) "已收藏" else "未收藏")
            Box(
                Modifier.width(if (large) 256.dp else 176.dp).height(if (large) 160.dp else 32.dp)
                    .background(backdrop).testTag("glyph-$selected-$large"),
            ) {
                FavoriteGlyph(selected, "128", iconSize = if (large) 144.dp else 24.dp)
            }
        }
    }
}
