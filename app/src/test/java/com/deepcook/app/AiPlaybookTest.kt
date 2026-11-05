package com.deepcook.app

import com.deepcook.app.data.ai.AssetPlaybook
import com.deepcook.app.data.ai.Playbook
import com.deepcook.app.data.ai.PlaybookEntry
import com.deepcook.app.data.ai.PlaybookFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiPlaybookTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val entries = listOf(
        PlaybookEntry("salty", listOf("咸", "太咸"), "加一点糖。"),
        PlaybookEntry("fire", listOf("着火", "起火"), "先关火，盖锅盖。"),
    )
    private val playbook = Playbook(entries)

    @Test fun aKeywordInsideTheQuestionSelectsItsAnswer() {
        assertEquals("fire", playbook.match("锅里着火了怎么办")?.id)
    }

    @Test fun spacingAndPunctuationDoNotBreakTheMatch() {
        assertEquals("fire", playbook.match("锅 里，起 火 了！")?.id)
    }

    @Test fun theLongestKeywordWins() {
        assertEquals("salty", playbook.match("有点太咸了")?.id)
    }

    @Test fun anUnknownQuestionFallsThroughToTheModel() {
        assertNull(playbook.match("红烧肉要炖多久"))
        assertNull(playbook.match("   "))
    }

    @Test fun theBundledPlaybookIsWellFormed() {
        val file = File("src/main/assets/${AssetPlaybook.ASSET}")
        assertTrue("playbook asset is missing", file.isFile)
        val playbookFile = json.decodeFromString<PlaybookFile>(file.readText())
        assertTrue(playbookFile.entries.size >= 30)
        assertEquals(
            playbookFile.entries.size,
            playbookFile.entries.map { it.id }.distinct().size,
        )
        playbookFile.entries.forEach { entry ->
            assertTrue(entry.id, entry.keywords.isNotEmpty())
            assertTrue(entry.id, entry.keywords.all { it.isNotBlank() })
            assertTrue(entry.id, entry.answer.isNotBlank())
            assertTrue(entry.id, entry.answer.length <= 60)
            assertTrue(entry.id, entry.tier == "safety" || entry.tier == "general")
        }
        // Every fixed answer must actually be reachable through its own keywords.
        playbookFile.entries.forEach { entry ->
            assertTrue(entry.id, Playbook(playbookFile.entries).match(entry.keywords.first())?.id == entry.id)
        }
    }
}
