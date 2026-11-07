package com.deepcook.app.data.ai

import com.deepcook.app.core.model.AiOverlay
import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.UserData

/**
 * Builds the dish-level facts for the detail page: the whole ingredient list, what the recipe
 * warns about, and this dish's own notes. The steps are reduced to their titles on purpose — a
 * substitution or a "why did it go wrong" question needs to know what the dish is, not the full
 * prose of every step, and a shorter prompt is a faster free model.
 */
class DishContextBuilder {
    fun messages(
        recipe: Recipe,
        user: UserData,
        question: String,
        overlay: AiOverlay? = null,
    ): List<AiMessage> = listOf(
        AiMessage.system(PromptRules.DISH + "\n\n" + facts(recipe, user, overlay)),
        AiMessage.user(question),
    )

    private fun facts(recipe: Recipe, user: UserData, overlay: AiOverlay?): String {
        val profile = user.profile
        val minutes = overlay?.minutes ?: recipe.minutes
        val difficulty = overlay?.difficulty ?: recipe.difficulty
        val servings = overlay?.servings?.takeIf { it.isNotBlank() } ?: recipe.servings
        val amountOf = { ingredient: Ingredient ->
            ingredientAmount(recipe, overlay, ingredient.id, ingredient.amount)
        }
        val notes = user.notesFor(recipe.id).take(2)
        return buildString {
            appendLine(
                "【这道菜】${recipe.title}｜${recipe.region}｜用时 ${minutes?.toString() ?: "未标注"} 分钟" +
                    "｜难度 ${difficulty?.let { "$it/5" } ?: "未标注"}｜份量 ${servings.ifBlank { "未标注" }}",
            )
            if (recipe.ingredients.isNotEmpty()) {
                appendLine(
                    "【材料】" + recipe.ingredients.take(20).joinToString("、") { "${it.name} ${amountOf(it)}" },
                )
            }
            if (recipe.preparationItems.isNotEmpty()) {
                appendLine("【准备】" + recipe.preparationItems.take(4).joinToString("；"))
            }
            appendLine("【步骤顺序】" + recipe.steps.joinToString("；") { "${it.order}. ${it.title}" })
            if (recipe.noteItems.isNotEmpty()) {
                appendLine("【注意事项】" + recipe.noteItems.take(4).joinToString("；"))
            }
            if (notes.isNotEmpty()) appendLine("【我的备注】" + notes.joinToString("；") { it.text })
            appendLine(
                "【用户】${profile.name}；喜好：${profile.likes.joinToString("、").ifBlank { "未填" }}" +
                    "；忌口：${profile.avoids.joinToString("、").ifBlank { "未填" }}",
            )
        }
    }

    companion object {
        /** A concrete amount the assistant filled in wins over the printed placeholder. */
        fun ingredientAmount(recipe: Recipe, overlay: AiOverlay?, id: String, printed: String): String =
            overlay?.amounts?.get(id)?.takeIf { it.isNotBlank() } ?: printed
    }
}
