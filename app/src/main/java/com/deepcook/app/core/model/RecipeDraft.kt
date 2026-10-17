package com.deepcook.app.core.model

/** One editable ingredient row. Blank rows are dropped when the draft becomes a [Recipe]. */
data class DraftIngredient(val name: String = "", val amount: String = "")

/** One editable step row. [cue] is the "观察提示" the reader shows under the step text. */
data class DraftStep(val title: String = "", val text: String = "", val cue: String = "")

/**
 * The editable shape of a user-authored recipe.
 *
 * The content model ([Recipe]) validates in its constructor and every field is required by
 * serialization; that is the right contract for checked-in content but unusable for a form the
 * user is still filling in. A draft may therefore be incomplete: the editor keeps one of these,
 * and only [toRecipe] turns it into a [Recipe] once [problems] is empty.
 */
data class RecipeDraft(
    val title: String = "",
    val region: String = "中式",
    val categories: List<String> = emptyList(),
    val minutes: Int? = null,
    val difficulty: Int? = null,
    val servings: String = "1 人份",
    val description: String = "",
    val preparation: List<String> = emptyList(),
    val ingredients: List<DraftIngredient> = listOf(DraftIngredient()),
    val steps: List<DraftStep> = listOf(DraftStep()),
    val notes: List<String> = emptyList(),
    val cover: String? = null,
    /** Where the prefilled text came from, kept as attribution after the user edits it. */
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    /** The text was prefilled from a public source and has not been cooked by anyone here. */
    val prefilled: Boolean = false,
) {
    /** Human-readable reasons this draft cannot be saved yet; empty means saveable. */
    fun problems(): List<String> = buildList {
        if (title.isBlank()) add("菜名不能为空")
        if (region !in RecipeRegions) add("地域需要从列表中选择")
        if (categories.isEmpty()) add("至少选择一个分类")
        if (categories.any { it !in RecipeCategories }) add("分类不在已登记的分类里")
        if (minutes != null && minutes !in 1..1440) add("用时需要在 1–1440 分钟之间")
        if (difficulty != null && difficulty !in 1..5) add("难度需要在 1–5 之间")
        if (servings.isBlank()) add("份量不能为空")
        if (ingredients.none { it.name.isNotBlank() }) add("至少填写一项材料")
        if (steps.none { it.title.isNotBlank() || it.text.isNotBlank() }) add("至少填写一个步骤")
    }

    /** True when the form has nothing the user typed — used to skip the discard prompt. */
    val untouched: Boolean
        get() = title.isBlank() &&
            description.isBlank() &&
            categories.isEmpty() &&
            ingredients.all { it.name.isBlank() && it.amount.isBlank() } &&
            steps.all { it.title.isBlank() && it.text.isBlank() && it.cue.isBlank() } &&
            preparation.isEmpty() &&
            notes.isEmpty() &&
            cover == null

    /**
     * Builds the content model. [id] must already be decided by the caller: an existing user
     * recipe keeps its id so favourites and history survive an edit.
     */
    fun toRecipe(id: String): Recipe {
        require(problems().isEmpty()) { problems().joinToString("；") }
        val keptIngredients = ingredients.filter { it.name.isNotBlank() }
        val keptSteps = steps.filter { it.title.isNotBlank() || it.text.isNotBlank() }
        return Recipe(
            id = id,
            version = 3,
            region = region,
            title = title.trim(),
            description = description.trim(),
            cover = cover,
            categories = categories,
            minutes = minutes,
            difficulty = difficulty,
            servings = servings.trim(),
            ingredients = keptIngredients.mapIndexed { index, ingredient ->
                Ingredient("i$index", ingredient.name.trim(), ingredient.amount.trim())
            },
            preparation = preparation.map(String::trim).filter(String::isNotEmpty),
            steps = keptSteps.mapIndexed { index, step ->
                RecipeStep(
                    id = "$id-s${index + 1}",
                    order = index + 1,
                    title = step.title.trim().ifEmpty { "第 ${index + 1} 步" },
                    text = step.text.trim(),
                    cue = step.cue.trim().ifEmpty { NO_CONTENT },
                )
            },
            notes = notes.map(String::trim).filter(String::isNotEmpty),
            source = RecipeSource(
                author = "我的菜谱",
                license = if (prefilled) {
                    "资料取自公开菜谱库（${sourceName ?: "HowToCook"}，Unlicense），已按你的修改保存；未经试做验证。"
                } else {
                    "由你自己添加的菜谱，仅保存在本机。"
                },
                url = sourceUrl,
            ),
            featured = false,
        )
    }

    companion object {
        /** Loads an existing recipe back into the editor; ids and order are not shown. */
        fun from(recipe: Recipe): RecipeDraft = RecipeDraft(
            title = recipe.title,
            region = recipe.region,
            categories = recipe.categories,
            minutes = recipe.minutes,
            difficulty = recipe.difficulty,
            servings = recipe.servings,
            description = recipe.description,
            preparation = recipe.preparationItems,
            ingredients = recipe.ingredients
                .map { DraftIngredient(it.name, it.amount) }
                .ifEmpty { listOf(DraftIngredient()) },
            steps = recipe.steps
                .sortedBy { it.order }
                .map { DraftStep(it.title, it.text, it.visibleCue.orEmpty()) }
                .ifEmpty { listOf(DraftStep()) },
            notes = recipe.noteItems,
            cover = recipe.cover,
            // Only a prefilled recipe carries a source url; the editor never sets one by hand.
            sourceUrl = recipe.source.url,
            sourceName = recipe.source.url?.let { "HowToCook" },
            prefilled = recipe.source.url != null,
        )
    }
}
