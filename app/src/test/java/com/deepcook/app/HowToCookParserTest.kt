package com.deepcook.app

import com.deepcook.app.data.lookup.HowToCookParser
import org.junit.Assert.*
import org.junit.Test

class HowToCookParserTest {
    /** Condensed from dishes/vegetable_dish/西红柿炒鸡蛋.md at the pinned commit. */
    private val page = """
        # 西红柿炒鸡蛋的做法

        一道酸甜开胃的家常菜肴，嫩滑的鸡蛋裹着软烂多汁的西红柿。步骤简单，原材料易得，从头备菜到盛盘出锅大约只需 15 分钟。

        预估烹饪难度：★★

        预估卡路里：252 大卡

        ## 必备原料和工具

        * 西红柿
        * 鸡蛋
        * 食用油
        * 盐
        * 糖（可选）
        * 葱花（可选）

        ## 计算

        每次制作前需要确定计划做几份。一份正好够 1 个人食用

        * 西红柿 = 1 个（约 180g） * 份数
        * 鸡蛋 = 1.5 个 * 份数，向上取整
        * 食用油 = 4ml * 鸡蛋/个
        * 盐 = 1.5-2g * 份数

        ## 操作

        1. 西红柿洗净
        2. 可选：去掉西红柿的外表皮

          - 开水烫表皮，然后将西红柿放入冷水，剥去外皮

        3. 西红柿去蒂，切成边长不超过 4cm 的小块，即为 `西红柿块`
        4. 热锅，加入食用油
        5. 油热后，倒入 `鸡蛋液`。翻炒至鸡蛋结为固体且颜色微微发黄
        6. 关火，盛盘

        ## 附加内容

        这道菜根据不同的口味偏好，存在诸多版本。

        * 快速做法：直接与鸡蛋一起翻炒
        * 加入盐 改为 “加入两滴生抽”
    """.trimIndent()

    @Test fun readsNameFromTheHeading() {
        val draft = HowToCookParser.parse(page, "vegetable_dish", "https://example.invalid/page.md")
        assertNotNull(draft)
        assertEquals("西红柿炒鸡蛋", draft!!.title)
        assertEquals("素食", draft.categories.single())
        assertEquals("无", draft.region)
    }

    @Test fun readsDescriptionDifficultyAndTime() {
        val draft = HowToCookParser.parse(page, "vegetable_dish", "https://example.invalid")!!
        assertTrue(draft.description.startsWith("一道酸甜开胃"))
        // Two stars in the page.
        assertEquals(2, draft.difficulty)
        // "大约只需 15 分钟" in the description.
        assertEquals(15, draft.minutes)
    }

    @Test fun mergesIngredientNamesWithCalculatedAmounts() {
        val draft = HowToCookParser.parse(page, "vegetable_dish", "https://example.invalid")!!
        val byName = draft.ingredients.associate { it.name to it.amount }
        assertEquals("1 个（约 180g） * 份数", byName["西红柿"])
        assertEquals("1.5 个 * 份数，向上取整", byName["鸡蛋"])
        assertEquals("4ml * 鸡蛋/个", byName["食用油"])
        assertTrue(byName.containsKey("糖（可选）"))
        // "鸡蛋/个" keeps its formula instead of dropping the row.
        assertNotEquals("", byName["鸡蛋"])
    }

    @Test fun numbersStepsAndKeepsSubBulletsAsCues() {
        val draft = HowToCookParser.parse(page, "vegetable_dish", "https://example.invalid")!!
        assertEquals(6, draft.steps.size)
        assertEquals("西红柿洗净", draft.steps.first().text)
        assertEquals("西红柿洗净", draft.steps.first().title)
        // The sub-bullet of step 2 becomes that step's observation hint.
        assertTrue(draft.steps[1].cue.contains("开水烫表皮"))
        assertTrue(draft.steps[1].text.contains("去掉西红柿的外表皮"))
        // Inline code markers are stripped from the body.
        assertFalse(draft.steps[2].text.contains("`"))
    }

    @Test fun takesNotesFromTheExtraSection() {
        val draft = HowToCookParser.parse(page, "vegetable_dish", "https://example.invalid")!!
        assertEquals(2, draft.notes.size)
        assertTrue(draft.notes.first().startsWith("快速做法"))
    }

    @Test fun marksTheDraftAsPrefilledAndKeepsTheSource() {
        val draft = HowToCookParser.parse(page, "soup", "https://example.invalid/番茄汤.md")!!
        assertTrue(draft.prefilled)
        assertEquals("HowToCook", draft.sourceName)
        assertEquals("https://example.invalid/番茄汤.md", draft.sourceUrl)
        assertEquals("汤羹", draft.categories.single())
    }

    @Test fun rejectsPagesWithoutUsableSteps() {
        val empty = """
            # 空气

            只有一段介绍。

            ## 必备原料和工具

            * 无
        """.trimIndent()
        assertNull(HowToCookParser.parse(empty, "vegetable_dish", "https://example.invalid"))
    }
}
