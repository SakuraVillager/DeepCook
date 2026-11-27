package com.deepcook.app

import com.deepcook.app.core.model.*
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class FoodRecommendationTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val fixture = json.parseToJsonElement(javaClass.getResource("/food-recommendations.json")!!.readText()).jsonObject
    private val recipes = json.decodeFromString<List<Recipe>>(fixture.getValue("recipes").toString())

    @Test fun sharedRankingFixturesMatchWebIncludingLegacyProfileTags() {
        fixture.getValue("cases").jsonArray.forEach { element ->
            val row = element.jsonObject
            val food = json.decodeFromString<FoodProfile>(row.getValue("profile").toString())
            val tags = row["userProfile"]?.let { json.decodeFromString<UserProfile>(it.toString()) } ?: UserProfile()
            val effective = UserData(foodSetup = FoodSetup(profile = food), profile = tags).recommendationProfile()
            val result = recipes.rankedRecommendations(effective)
            val expected = row.getValue("ids").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(row.getValue("name").jsonPrimitive.content, expected, result.map { it.recipe.id })
            assertTrue(result.all { it.reasons.isNotEmpty() && it.recipe.foodAssessment(effective).eligible })
        }
    }

    @Test fun warningReportsMatchedFoodsAndIncompleteData() {
        val profile = FoodProfile(allergens = setOf("大豆"), avoided = setOf("猪肉"))
        val result = recipes.first().foodAssessment(profile)
        assertEquals(listOf("allergen", "avoid"), result.conflicts.map { it.kind })
        assertEquals(listOf("生抽"), result.conflicts.first().ingredients)
        assertEquals(listOf("五花肉"), result.conflicts.last().ingredients)
        assertTrue(recipes.first { it.id == "unknown" }.foodAssessment(profile).incomplete)
        val trace = recipes.first { it.id == "soup" }.foodAssessment(FoodProfile(allergens = setOf("花生")))
        assertTrue(trace.conflicts.single().ingredients.single().contains("步骤或准备"))
        assertTrue(recipes.first { it.id == "mushroom" }.foodAssessment(profile).eligible)
    }

    @Test fun avoidedFlavourOverridesLikesAndDuplicateRecipesAreDeduplicated() {
        val profile = UserData(foodSetup = FoodSetup(profile = FoodProfile(tastes = setOf("辣"))),
            profile = UserProfile(likes = setOf("辣", "酸甜"), avoids = setOf("辣"))).recommendationProfile()
        assertEquals(setOf("酸甜"), profile.tastes)
        assertEquals(recipes.forFoodProfile(profile), (recipes + recipes).forFoodProfile(profile))
    }
}
