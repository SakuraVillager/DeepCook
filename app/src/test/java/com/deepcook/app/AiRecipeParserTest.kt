package com.deepcook.app

import com.deepcook.app.core.model.AI_DRAFT_SOURCE
import com.deepcook.app.data.lookup.AiRecipeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecipeParserTest {
    private val valid = """
        {"found": true, "description": "一道家常菜。", "region": "中式",
         "categories": ["家常菜", "不存在的分类"], "minutes": 45, "difficulty": 3, "servings": "4 人份",
         "ingredients": [{"name": "五花肉", "amount": "500 克"}, {"name": "冰糖", "amount": "2 块"},
                         {"name": "五花肉", "amount": ""}, {"name": "  ", "amount": "少许"}],
         "steps": [{"title": "焯水", "text": "冷水下锅焯三分钟。", "cue": "水面浮沫"},
                   {"title": "", "text": ""}],
         "notes": ["糖色别炒过头", ""]}
    """.trimIndent()

    @Test fun aUsableReplyBecomesADraft() {
        val draft = AiRecipeParser.parse("红烧肉", valid)!!
        assertEquals("红烧肉", draft.title)
        assertEquals("中式", draft.region)
        assertEquals(listOf("家常菜"), draft.categories)
        assertEquals(45, draft.minutes)
        assertEquals(3, draft.difficulty)
        assertEquals("4 人份", draft.servings)
        assertEquals(listOf("五花肉", "冰糖"), draft.ingredients.map { it.name })
        // A made-up "500 克" is dropped; a countable "2 块" survives and the user fills the rest.
        assertEquals("", draft.ingredients[0].amount)
        assertEquals("2 块", draft.ingredients[1].amount)
        assertEquals(listOf("焯水"), draft.steps.map { it.title })
        assertEquals(listOf("糖色别炒过头"), draft.notes)
        assertEquals(AI_DRAFT_SOURCE, draft.sourceName)
        assertNull(draft.sourceUrl)
        assertTrue(draft.prefilled)
        assertTrue(draft.problems().isEmpty())
    }

    @Test fun aRefusalOrProseYieldsNothing() {
        assertNull(AiRecipeParser.parse("某菜", """{"found": false}"""))
        assertNull(AiRecipeParser.parse("某菜", "我不知道这道菜，没法写。"))
        assertNull(AiRecipeParser.parse("某菜", """{"found": true, "ingredients": [], "steps": []}"""))
        assertNull(AiRecipeParser.parse("某菜", """{"found": true, "ingredients": [{"name": "盐"}], "steps": []}"""))
    }

    @Test fun aFencedReplyIsStillParsed() {
        val fenced = "```json\n$valid\n```"
        assertEquals("红烧肉", AiRecipeParser.parse("红烧肉", fenced)!!.title)
    }

    @Test fun outOfRangeAndUnknownValuesAreReplacedOrDropped() {
        val raw = """{"found": true, "region": "火星", "categories": ["没这个"], "minutes": 9999,
            "difficulty": 9, "servings": "很多人", "description": "",
            "ingredients": [{"name": "鸡蛋", "amount": "适量"}],
            "steps": [{"text": "打散炒熟。"}]}"""
        val draft = AiRecipeParser.parse("炒蛋", raw)!!
        assertEquals("中式", draft.region)
        assertEquals(listOf("家常菜"), draft.categories)
        assertNull(draft.minutes)
        assertNull(draft.difficulty)
        assertEquals("很多人", draft.servings)
        assertEquals("适量", draft.ingredients.single().amount)
        // A step without a title is fine: saving fills "第 N 步".
        assertEquals("", draft.steps.single().title)
        assertTrue(draft.problems().isEmpty())
    }

    @Test fun theNumberOfRowsIsBounded() {
        val ingredients = (1..20).joinToString(",") { """{"name": "材料$it"}""" }
        val steps = (1..20).joinToString(",") { """{"title": "步骤$it", "text": "做第 $it 件事。"}""" }
        val raw = """{"found": true, "ingredients": [$ingredients], "steps": [$steps]}"""
        val draft = AiRecipeParser.parse("很多步的菜", raw)!!
        assertEquals(14, draft.ingredients.size)
        assertEquals(12, draft.steps.size)
    }
}
