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
) {
    /** An observation cue that content left empty is stored as [NO_CONTENT] and must stay hidden. */
    val visibleCue: String? get() = cue?.trim()?.takeUnless { it.isEmpty() || it == NO_CONTENT }
}

@Serializable
data class RecipeSource(
    val author: String = "DeepCook 示例内容",
    val license: String = "自编演示菜谱，未经独立试做验证；图片来自 HowToCook（Unlicense）。",
    val url: String? = null,
    val imageSource: String? = null,
)

/**
 * Content convention: an element the app displays but a recipe does not provide is stored as
 * this placeholder. Every recipe then carries every field, while the UI hides the placeholders
 * instead of rendering rows that only say "无".
 */
const val NO_CONTENT = "无"

/**
 * Recipes the user writes themselves live alongside the bundled catalog. Their ids all start
 * with this prefix, which is how the app tells "my recipe" from "shipped content" without a
 * second field in the content format: it survives storage, needs no migration, and cannot
 * collide with the hand-written ids in `assets/recipes.json`.
 */
const val USER_RECIPE_ID_PREFIX = "user-"

/** True for a recipe the user created or edited on this device. */
val Recipe.isMine: Boolean get() = id.startsWith(USER_RECIPE_ID_PREFIX)

/** Region labels: the content, the card/detail tag and the category picker all use this list. */
val RecipeRegions = listOf("中式", "日式", "韩式", "美式", "欧式", "东南亚", "其他")

/**
 * Dish-style categories. A recipe's origin is tracked by [Recipe.region] instead of being
 * repeated here, so region filtering stays independent from how a dish is cooked.
 */
val RecipeCategories = listOf(
    "家常菜",
    "快手菜",
    "素食",
    "肉类",
    "海鲜",
    "汤羹",
    "主食",
    "面食",
    "烘焙甜点",
    "早餐",
    "沙拉",
)

/**
 * Category picker entries. Regions come first because they are the coarse way to browse the
 * expanded catalog, then the dish styles, then "全部"; the same list drives the home chips and
 * the search dropdown so both entry points always agree.
 */
val RecipeCategoryOptions: List<String> = listOf("全部") + RecipeRegions + RecipeCategories

private fun List<String>.withoutPlaceholder(): List<String> =
    filterNot { it.isBlank() || it.trim() == NO_CONTENT }

@Serializable
data class Recipe(
    val id: String,
    val version: Int = 1,
    val region: String = "其他",
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
        require(region in RecipeRegions) { "菜谱地域必须为已登记的地域：$region" }
        require(categories.isNotEmpty()) { "菜谱至少需要一个分类" }
        require(categories.all { it in RecipeCategories }) { "菜谱分类必须为已登记的分类：$categories" }
    }

    /** Preparation entries without the [NO_CONTENT] placeholder; empty means "hide the section". */
    val preparationItems: List<String> get() = preparation.withoutPlaceholder()

    /** Note entries without the [NO_CONTENT] placeholder; empty means "hide the section". */
    val noteItems: List<String> get() = notes.withoutPlaceholder()
}

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

/** A picker value selects a region or one of the dish-style tags; "全部" selects everything. */
private fun String.matches(recipe: Recipe): Boolean =
    this == "全部" || (this in RecipeRegions && recipe.region == this) || this in recipe.categories

fun List<Recipe>.filteredBy(filter: RecipeFilter): List<Recipe> {
    val terms = filter.query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return filter { recipe ->
        // Region labels are searchable by name as well, so "韩式" works from the text field too.
        val searchable = listOf(recipe.title, recipe.region) + recipe.ingredients.map(Ingredient::name)
        terms.all { term -> searchable.any { it.contains(term, ignoreCase = true) } } &&
            filter.category.matches(recipe) &&
            filter.time.matches(recipe.minutes) &&
            (filter.difficulty == null || filter.difficulty == recipe.difficulty)
    }
}
