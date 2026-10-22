package com.deepcook.app.core.model

import kotlinx.serialization.Serializable

/**
 * What the user tells DeepCook about themselves: a name, a picture, the flavours they enjoy and
 * the ingredients they would rather not eat.
 *
 * Tags are plain strings next to the favourites and settings in [UserData]; [sanitized] drops
 * anything this build does not offer, so a removed flavour disappears instead of lingering in the
 * multiselect. Nothing here is sent anywhere: the profile only leaves the phone when the user
 * exports their data.
 */
@Serializable
data class UserProfile(
    /** Blank means "no name chosen"; [name] then falls back to [DEFAULT_NAME]. */
    val displayName: String = "",
    /** `file:` URI of the copy in the app's own storage, or null when no picture was chosen. */
    val avatar: String? = null,
    val likes: Set<String> = emptySet(),
    val avoids: Set<String> = emptySet(),
) {
    /** The heading to show: an unnamed kitchen still needs a name. */
    val name: String get() = displayName.ifBlank { DEFAULT_NAME }

    /**
     * Adds or removes a liked flavour. Turning one on drops it from [avoids]: the same tag cannot
     * be both wanted and avoided, and leaving both selected would make the two lists contradict
     * each other once they start driving recommendations.
     */
    fun withLike(tag: String, on: Boolean): UserProfile =
        copy(likes = likes.changed(tag, on), avoids = if (on) avoids - tag else avoids)

    /** Adds or removes an avoided ingredient, dropping it from [likes] when it is turned on. */
    fun withAvoid(tag: String, on: Boolean): UserProfile =
        copy(avoids = avoids.changed(tag, on), likes = if (on) likes - tag else likes)

    /**
     * Trims the name, caps its length and drops tags this build no longer offers.
     *
     * Applied on every write, so an unknown tag can never come back through a restored file.
     */
    fun sanitized(): UserProfile = copy(
        displayName = capName(displayName.trim()),
        likes = likes.filterTo(LinkedHashSet(), LIKE_TAGS::contains),
        avoids = avoids.filterTo(LinkedHashSet(), AVOID_TAGS::contains),
    )

    private fun Set<String>.changed(tag: String, on: Boolean): Set<String> =
        if (on) this + tag else this - tag

    companion object {
        const val DEFAULT_NAME = "我的厨房"
        const val MAX_NAME_LENGTH = 12

        val LIKE_TAGS = listOf("甜", "咸", "辣", "酸", "麻", "鲜", "清淡", "油炸", "汤羹", "下饭")

        val AVOID_TAGS = listOf("辣", "香菜", "葱", "姜", "蒜", "海鲜", "花生", "牛肉", "羊肉", "内脏")

        /** Caps a name without splitting a surrogate pair in half. */
        fun capName(value: String): String =
            if (value.codePointCount(0, value.length) <= MAX_NAME_LENGTH) {
                value
            } else {
                value.substring(0, value.offsetByCodePoints(0, MAX_NAME_LENGTH))
            }

        /** Number of characters a user would count, not the number of UTF-16 units. */
        fun nameLength(value: String): Int = value.codePointCount(0, value.length)
    }
}
