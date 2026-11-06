package com.deepcook.app.data.ai

import com.deepcook.app.core.model.AiOverlay
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.UserData

/**
 * Builds the facts the assistant is allowed to see. Everything here is either the dish being
 * cooked or a small piece of the user's own profile — never the whole catalog, and never more
 * history than the last two turns.
 */
class ContextBuilder {
    fun messages(
        recipe: Recipe,
        stepIndex: Int,
        user: UserData,
        titles: Map<String, String>,
        question: String,
        history: List<AiExchange>,
        overlay: AiOverlay? = null,
    ): List<AiMessage> {
        val messages = ArrayList<AiMessage>(8)
        messages += AiMessage.system(PromptRules.SYSTEM + "\n\n" + facts(recipe, stepIndex, user, titles, overlay))
        history.takeLast(2).forEach { exchange ->
            messages += AiMessage.user(exchange.question)
            messages += AiMessage.assistant(exchange.answer)
        }
        messages += AiMessage.user(question)
        return messages
    }

    private fun facts(
        recipe: Recipe,
        stepIndex: Int,
        user: UserData,
        titles: Map<String, String>,
        overlay: AiOverlay?,
    ): String {
        val step = recipe.steps.getOrNull(stepIndex) ?: recipe.steps.first()
        val profile = user.profile
        val minutes = overlay?.minutes ?: recipe.minutes
        val difficulty = overlay?.difficulty ?: recipe.difficulty
        val servings = overlay?.servings?.takeIf { it.isNotBlank() } ?: recipe.servings
        val amountOf = { id: String, fallback: String -> overlay?.amounts?.get(id) ?: fallback }
        val recent = user.recent.mapNotNull { titles[it.recipeId] }.distinct().take(3)
        val frequent = user.recent.groupingBy { it.recipeId }.eachCount()
            .entries.sortedByDescending { it.value }.take(3)
            .mapNotNull { titles[it.key] }
            .distinct()
        val saved = user.favorites.mapNotNull { titles[it.recipeId] }.take(3)
        return buildString {
            appendLine(
                "【在做的菜】${recipe.title}｜${recipe.region}｜用时 ${minutes?.toString() ?: "未标注"} 分钟" +
                    "｜难度 ${difficulty?.let { "$it/5" } ?: "未标注"}｜${servings.ifBlank { "份量未标注" }}",
            )
            appendLine("【当前步骤】第 ${stepIndex + 1}/${recipe.steps.size} 步「${step.title}」：${step.text}")
            step.visibleCue?.let { appendLine("【观察提示】$it") }
            appendLine(
                "【材料】" + recipe.ingredients.take(12).joinToString("、") { "${it.name} ${amountOf(it.id, it.amount)}" },
            )
            val stepIngredients = recipe.ingredients.filter { it.id in step.ingredientRefs }
            if (stepIngredients.isNotEmpty()) {
                appendLine(
                    "【本步材料】" + stepIngredients.joinToString("、") { "${it.name} ${amountOf(it.id, it.amount)}" },
                )
            }
            appendLine(
                "【用户】${profile.name}；喜好：${profile.likes.joinToString("、").ifBlank { "未填" }}" +
                    "；忌口：${profile.avoids.joinToString("、").ifBlank { "未填" }}",
            )
            if (saved.isNotEmpty()) appendLine("【收藏过的】${saved.joinToString("、")}")
            if (recent.isNotEmpty()) appendLine("【最近看过】${recent.joinToString("、")}")
            if (frequent.isNotEmpty()) appendLine("【常做】${frequent.joinToString("、")}")
        }
    }
}
