package com.deepcook.app

import com.deepcook.app.data.lookup.DishEntry
import com.deepcook.app.data.lookup.DishMatcher
import org.junit.Assert.*
import org.junit.Test

class DishMatcherTest {
    private val catalog = listOf(
        DishEntry("西红柿炒鸡蛋", "dishes/vegetable_dish/西红柿炒鸡蛋.md", "vegetable_dish"),
        DishEntry("西红柿牛腩", "dishes/meat_dish/西红柿牛腩/西红柿牛腩.md", "meat_dish"),
        DishEntry("西红柿鸡蛋汤", "dishes/soup/西红柿鸡蛋汤.md", "soup"),
        DishEntry("小龙虾", "dishes/aquatic/小龙虾/小龙虾.md", "aquatic"),
        DishEntry("冬瓜排骨汤", "dishes/soup/冬瓜排骨汤.md", "soup"),
        DishEntry("排骨", "dishes/meat_dish/排骨.md", "meat_dish"),
    )

    @Test fun exactNameWins() {
        assertEquals("小龙虾", DishMatcher.match("小龙虾", catalog)?.name)
    }

    @Test fun ignoresSpacesAndPunctuation() {
        assertEquals("西红柿炒鸡蛋", DishMatcher.match(" 西红柿 炒鸡蛋 ", catalog)?.name)
        assertEquals("小龙虾", DishMatcher.match("小龙虾！", catalog)?.name)
    }

    @Test fun foldsCommonIngredientSynonyms() {
        // 番茄 and 西红柿 are the same ingredient; the catalog only spells it one way.
        assertEquals("西红柿炒鸡蛋", DishMatcher.match("番茄炒鸡蛋", catalog)?.name)
    }

    @Test fun prefersTheMostSpecificContainedName() {
        // A decorated name still resolves through the contained dish name.
        assertEquals("冬瓜排骨汤", DishMatcher.match("冬瓜排骨汤（家常版）", catalog)?.name)
        assertEquals("西红柿炒鸡蛋", DishMatcher.match("西红柿炒鸡蛋饭", catalog)?.name)
        assertEquals("排骨", DishMatcher.match("排骨", catalog)?.name)
    }

    @Test fun acceptsACloseNameThatIsNotSpelledTheSame() {
        assertEquals("西红柿炒鸡蛋", DishMatcher.match("番茄炒蛋", catalog)?.name)
    }

    @Test fun refusesDishesThatOnlyLookSimilar() {
        // 韭黄炒鸡蛋 is a different dish; it must not borrow 西红柿炒鸡蛋's page.
        assertNull(DishMatcher.match("韭黄炒鸡蛋", catalog))
        assertNull(DishMatcher.match("红烧狮子头", catalog))
        assertNull(DishMatcher.match("", catalog))
    }

    @Test fun bigramOverlapIsZeroForUnrelatedNames() {
        assertEquals(0.0, DishMatcher.overlap("鱼香肉丝", "提拉米苏"), 0.0001)
        assertEquals(1.0, DishMatcher.overlap("宫保鸡丁", "宫保鸡丁"), 0.0001)
    }
}
