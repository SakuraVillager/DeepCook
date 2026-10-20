package com.deepcook.app

import com.deepcook.app.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileTest {
    @Test fun anUnnamedKitchenFallsBackToTheDefaultHeading() {
        assertEquals(UserProfile.DEFAULT_NAME, UserProfile().name)
        assertEquals(UserProfile.DEFAULT_NAME, UserProfile(displayName = "   ").name)
    }

    @Test fun aChosenNameReplacesTheDefaultHeading() {
        assertEquals("小明", UserProfile(displayName = "小明").name)
    }

    @Test fun togglingATagOnlyTouchesItsOwnList() {
        val liked = UserProfile().withLike("辣", true).withLike("鲜", true)
        assertEquals(setOf("辣", "鲜"), liked.likes)
        assertEquals(emptySet<String>(), liked.avoids)
        assertEquals(setOf("鲜"), liked.withLike("辣", false).likes)
    }

    @Test fun aTagCannotBeLikedAndAvoidedAtOnce() {
        val avoided = UserProfile().withLike("辣", true).withAvoid("辣", true)
        assertEquals(setOf("辣"), avoided.avoids)
        assertEquals(emptySet<String>(), avoided.likes)
        val likedAgain = avoided.withLike("辣", true)
        assertEquals(setOf("辣"), likedAgain.likes)
        assertEquals(emptySet<String>(), likedAgain.avoids)
    }

    @Test fun sanitizingTrimsTheNameAndDropsTagsThisBuildDoesNotOffer() {
        val cleaned = UserProfile(
            displayName = "  小明  ",
            likes = linkedSetOf("辣", "外星口味"),
            avoids = linkedSetOf("香菜", "石头"),
        ).sanitized()
        assertEquals("小明", cleaned.displayName)
        assertEquals(setOf("辣"), cleaned.likes)
        assertEquals(setOf("香菜"), cleaned.avoids)
    }

    @Test fun sanitizingCapsTheNameWithoutSplittingSurrogatePairs() {
        val noodles = "🍜"
        val cleaned = UserProfile(displayName = noodles.repeat(20)).sanitized()
        assertEquals(noodles.repeat(UserProfile.MAX_NAME_LENGTH), cleaned.displayName)
        assertEquals(UserProfile.MAX_NAME_LENGTH, UserProfile.nameLength(cleaned.displayName))
    }

    @Test fun nameLengthCountsWhatTheUserSees() {
        assertEquals(2, UserProfile.nameLength("🍜🍜"))
        assertEquals(4, UserProfile.nameLength("阿明ab"))
        assertTrue(UserProfile.capName("abcdefghijklmnop") == "abcdefghijkl")
    }
}
