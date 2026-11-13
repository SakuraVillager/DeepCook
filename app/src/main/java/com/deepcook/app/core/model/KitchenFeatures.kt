package com.deepcook.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Nutrition(
    val calories: Int?,
    val fat: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fiber: Double? = null,
    val sodium: Int? = null,
)

data class KitchenPost(
    val id: String,
    val recipeId: String,
    val submission: Boolean,
    val text: String,
    val photo: String?,
    val author: String,
    val sample: Boolean,
    val createdAt: Long,
    val mine: Boolean = !sample,
)

data class RecipeCommunity(
    val nutrition: Nutrition? = null,
    val favoriteExamples: Int = 0,
    val ratings: List<Int> = emptyList(),
    val ownRating: Int? = null,
    val posts: List<KitchenPost> = emptyList(),
    val ratingCounts: List<Int>? = null,
    val commentCount: Int? = null,
    val submissionCount: Int? = null,
    val commentCursor: String? = null,
    val submissionCursor: String? = null,
    val cloud: Boolean = false,
) {
    val ratingCount: Long get() = ratingCounts?.sumOf { it.toLong() } ?: ratings.size.toLong()
    fun countScores(range: IntRange): Long = ratingCounts?.let { counts -> range.sumOf { counts.getOrElse(it - 1) { 0 }.toLong() } }
        ?: ratings.count { it in range }.toLong()
    val average: Double? get() = if (ratingCounts != null) {
        if (ratingCount == 0L) null else ratingCounts.mapIndexed { index, count -> (index + 1).toDouble() * count }.sum() / ratingCount
    } else ratings.takeIf { it.isNotEmpty() }?.average()
}

fun compactCount(count: Int): String = when {
    count >= 10_000 -> java.lang.String.format(java.util.Locale.ROOT, "%.1fw", count / 10_000.0).replace(".0w", "w")
    count >= 1_000 -> java.lang.String.format(java.util.Locale.ROOT, "%.1fk", count / 1_000.0).replace(".0k", "k")
    else -> count.coerceAtLeast(0).toString()
}
