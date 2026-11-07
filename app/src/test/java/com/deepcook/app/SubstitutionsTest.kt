package com.deepcook.app

import com.deepcook.app.data.ai.DishAnswer
import com.deepcook.app.data.ai.Substitutions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubstitutionsTest {
    @Test fun aKnownPantryItemGetsAnInstantAnswer() {
        val answer = Substitutions.find("蚝油")
        assertTrue(answer != null && answer.contains("生抽"))
    }

    @Test fun theMostSpecificNameWins() {
        assertTrue(Substitutions.find("玉米淀粉")!!.contains("土豆淀粉"))
        assertTrue(Substitutions.find("淀粉")!!.contains("面粉"))
    }

    @Test fun anUnknownIngredientFallsThrough() {
        assertNull(Substitutions.find("牛腩"))
        assertNull(Substitutions.find("   "))
    }

    @Test fun everyLocalAnswerFitsTheAnswerCard() {
        Substitutions.Table.forEach { (key, answer) ->
            assertTrue("关键词不能为空：$answer", key.isNotBlank())
            assertTrue("答案太长：$answer", answer.length <= 60)
            assertEquals("答案必须不超过两句：$answer", answer, DishAnswer.twoSentences(answer))
        }
    }
}
