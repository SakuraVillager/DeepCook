package com.deepcook.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FoodProfile(
    val cuisines: Set<String> = emptySet(),
    val spice: String? = null,
    val tastes: Set<String> = emptySet(),
    val diet: String? = null,
    /** null = unanswered; empty = explicitly no known allergies/intolerances. */
    val allergens: Set<String>? = null,
    val avoided: Set<String> = emptySet(),
    val experience: String? = null,
    val minutes: Int? = null,
    val servings: String? = null,
    val goals: Set<String> = emptySet(),
) {
    val hasAnswers: Boolean get() = cuisines.isNotEmpty() || spice != null || tastes.isNotEmpty() ||
        diet != null || allergens != null || avoided.isNotEmpty() || experience != null ||
        minutes != null || servings != null || goals.isNotEmpty()
}

@Serializable
data class FoodSetup(
    val profile: FoodProfile = FoodProfile(),
    val draft: FoodProfile? = null,
    val page: Int = 0,
    val completed: Boolean = false,
)

object FoodChoices {
    val cuisines = listOf("中式家常", "川湘风味", "粤式风味", "江浙风味", "日韩料理", "东南亚菜", "西式料理", "其他风味")
    val spices = listOf("不吃辣", "微辣", "中辣", "重辣", "都可以")
    val tastes = listOf("清淡", "鲜香", "酸甜", "浓郁", "都喜欢")
    val diets = listOf("不限制", "蛋奶素", "纯素")
    val allergens = listOf("牛奶及乳制品", "鸡蛋", "花生", "坚果", "大豆", "小麦", "鱼类", "虾蟹", "贝类", "芝麻")
    val avoided = listOf("香菜", "葱", "蒜", "芹菜", "胡萝卜", "茄子", "蘑菇", "动物内脏", "牛肉", "羊肉", "猪肉")
    val experience = listOf("刚开始学", "会做几道家常菜", "经常下厨")
    val goals = listOf("找到今天吃什么", "学会做新菜", "快手解决一餐", "尝试不同风味", "记录自己的拿手菜")
    val goalNotes = listOf("多一点日常做饭灵感", "从清晰的步骤开始", "优先发现省时的做法", "探索各地和世界料理", "整理、保存自己的做法")
}

/** Both generations of profile tags participate; exclusions take priority over tastes. */
fun UserData.recommendationProfile(): FoodProfile {
    val avoided = foodSetup.profile.avoided + profile.avoids
    return foodSetup.profile.copy(avoided = avoided, tastes = (foodSetup.profile.tastes + profile.likes) - avoided)
}

private fun String.containsAny(terms: List<String>): Boolean = terms.any { contains(it, ignoreCase = true) }

val FoodProfile.hasRestrictions: Boolean get() = !allergens.isNullOrEmpty() || avoided.isNotEmpty() ||
    spice == "不吃辣" || diet in listOf("蛋奶素", "纯素")

data class FoodConflict(val kind: String, val label: String, val ingredients: List<String>)
data class FoodAssessment(val conflicts: List<FoodConflict>, val incomplete: Boolean) {
    val eligible: Boolean get() = conflicts.isEmpty() && !incomplete
}

val foodRestrictionNotice: String get() = FoodRules.notice

/** Conservative text matching, including ingredients mentioned only in preparation/steps. */
fun Recipe.foodAssessment(profile: FoodProfile): FoodAssessment {
    val names = ingredients.map { it.name.trim() }
    val instructions = preparation + steps.map { it.text }
    val conflicts = mutableListOf<FoodConflict>()
    fun add(kind: String, label: String, terms: List<String>, clean: (String) -> String = { it }) {
        val matched = names.filter { clean(it).containsAny(terms) }.toMutableList()
        if (instructions.any { clean(it).containsAny(terms) }) matched += "步骤或准备中提及相关食材"
        if (matched.isNotEmpty()) conflicts += FoodConflict(kind, label, matched.distinct())
    }
    fun plantFree(text: String) = FoodRules.plantNames.fold(text) { value, term -> value.replace(term, "") }
    fun aliasClean(term: String): (String) -> String = if (term in listOf("牛奶及乳制品", "鱼类", "贝类", "海鲜", "动物内脏", "内脏")) ::plantFree else { text -> text }
    profile.allergens.orEmpty().forEach { add("allergen", it, FoodRules.aliases[it] ?: listOf(it), aliasClean(it)) }
    profile.avoided.forEach { add("avoid", it, FoodRules.aliases[it] ?: listOf(it), aliasClean(it)) }
    if (profile.spice == "不吃辣") add("spice", "不吃辣", FoodRules.aliases.getValue("辣"))
    if (profile.diet in listOf("蛋奶素", "纯素")) {
        add("diet", profile.diet!!, FoodRules.animal) {
            plantFree(it).replace("鸡蛋", "").replace("鸭蛋", "").replace("鹌鹑蛋", "")
        }
    }
    if (profile.diet == "纯素") add("diet", "纯素（蛋奶及蜂蜜）", FoodRules.nonVegan, ::plantFree)
    val incomplete = profile.hasRestrictions && (names.isEmpty() || names.any { it.isBlank() || it == NO_CONTENT })
    return FoodAssessment(conflicts, incomplete)
}

fun Recipe.matchesFoodExclusions(profile: FoodProfile): Boolean = foodAssessment(profile).eligible

data class FoodRecommendation(val recipe: Recipe, val score: Int, val reasons: List<String>)

/** Filter first, score once, preserve input order for equal scores. Never backfill excluded dishes. */
fun List<Recipe>.rankedRecommendations(profile: FoodProfile): List<FoodRecommendation> {
    val unique = distinctBy { it.id }
    if (!profile.hasAnswers) return unique.filter { it.featured }.take(6).map { FoodRecommendation(it, 1, listOf("精选菜谱")) }
    return unique.filter { it.matchesFoodExclusions(profile) }.map { recipe ->
        val text = (listOf(recipe.title, recipe.description, recipe.region) + recipe.categories + recipe.ingredients.map { it.name }).joinToString(" ")
        val reasons = mutableListOf<String>()
        var points = if (recipe.featured) FoodRules.weights.getValue("featured") else 0
        fun reward(condition: Boolean, weight: String, reason: String, multiplier: Int = 1) {
            if (condition) { points += FoodRules.weights.getValue(weight) * multiplier; reasons += reason }
        }
        val cuisines = profile.cuisines.filter { text.containsAny(FoodRules.cuisines[it].orEmpty()) }
        reward(cuisines.isNotEmpty(), "cuisine", cuisines.joinToString("、"))
        val tastes = profile.tastes.filter { it != "都喜欢" && text.containsAny(FoodRules.tastes[it] ?: listOf(it)) }
        reward(tastes.isNotEmpty(), "taste", "口味：${tastes.joinToString("、")}", tastes.size.coerceAtMost(3))
        reward(profile.minutes != null && profile.minutes > 0 && recipe.minutes != null && recipe.minutes <= profile.minutes, "time", "符合用时偏好")
        reward(profile.experience == "刚开始学" && recipe.difficulty != null && recipe.difficulty <= 2, "beginner", "新手易上手")
        reward("快手解决一餐" in profile.goals && recipe.minutes != null && recipe.minutes <= 30, "quick", "快手一餐")
        reward("学会做新菜" in profile.goals && recipe.difficulty != null && recipe.difficulty <= 2, "learn", "适合学做新菜")
        reward("尝试不同风味" in profile.goals && recipe.region !in listOf("中式", NO_CONTENT), "explore", "探索不同风味")
        reward(profile.spice in listOf("中辣", "重辣") && recipe.ingredients.any { it.name.containsAny(FoodRules.aliases.getValue("辣")) }, "spicy", "偏爱的辣味")
        FoodRecommendation(recipe, points, reasons.ifEmpty { listOf("换一道日常灵感") })
    }.sortedByDescending { it.score }.take(6)
}

fun List<Recipe>.forFoodProfile(profile: FoodProfile): List<Recipe> = rankedRecommendations(profile).map { it.recipe }
