package com.deepcook.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Ingredient(val id: String, val name: String, val amount: String)

@Serializable
data class RecipeStep(
    val id: String,
    val order: Int,
    val title: String,
    val text: String,
    val image: String? = null,
    val ingredientRefs: List<String> = emptyList(),
    val cue: String? = null,
)

@Serializable
data class RecipeSource(
    val author: String = "DeepCook 示例内容",
    val license: String = "自编演示菜谱，未经独立试做验证；图片来自 HowToCook（Unlicense）。",
    val url: String? = null,
    val imageSource: String? = null,
)

@Serializable
data class Recipe(
    val id: String,
    val version: Int = 1,
    val title: String,
    val description: String,
    val cover: String? = null,
    val categories: List<String>,
    val minutes: Int? = null,
    val difficulty: Int? = null,
    val servings: String,
    val ingredients: List<Ingredient>,
    val preparation: List<String> = emptyList(),
    val steps: List<RecipeStep>,
    val notes: List<String> = emptyList(),
    val source: RecipeSource = RecipeSource(),
    val featured: Boolean = false,
) {
    init {
        require(difficulty == null || difficulty in 1..5) { "菜谱难度必须为 1–5 分" }
    }
}

val RecipeCategories = listOf("全部", "家常菜", "快手菜", "素食", "肉类", "海鲜", "汤羹")

enum class TimeFilter(val label: String) {
    ANY("不限用时"),
    FIFTEEN("15 分钟以内"),
    THIRTY("30 分钟以内"),
    SIXTY("60 分钟以内"),
    LONG("60 分钟以上"),
    ;

    fun matches(minutes: Int?): Boolean = when (this) {
        ANY -> true
        FIFTEEN -> minutes != null && minutes <= 15
        THIRTY -> minutes != null && minutes <= 30
        SIXTY -> minutes != null && minutes <= 60
        LONG -> minutes != null && minutes > 60
    }
}

data class RecipeFilter(
    val query: String = "",
    val category: String = "全部",
    val time: TimeFilter = TimeFilter.ANY,
    val difficulty: Int? = null,
) {
    val active: Boolean get() = category != "全部" || time != TimeFilter.ANY || difficulty != null
}

fun List<Recipe>.filteredBy(filter: RecipeFilter): List<Recipe> {
    val terms = filter.query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return filter { recipe ->
        val searchable = listOf(recipe.title) + recipe.ingredients.map(Ingredient::name)
        terms.all { term -> searchable.any { it.contains(term, ignoreCase = true) } } &&
            (filter.category == "全部" || filter.category in recipe.categories) &&
            filter.time.matches(recipe.minutes) &&
            (filter.difficulty == null || filter.difficulty == recipe.difficulty)
    }
}
