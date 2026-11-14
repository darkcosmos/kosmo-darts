package io.github.darkcosmos.kosmodarts.engine.profile

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** History rules PM3, PM4, PM6, PM7, PM8. */
class ProfileRulesTest {

    @Test
    fun `PM3 blank name is invalid`() {
        assertEquals(RenameOutcome.Invalid(PlayerListError.BLANK_NAME), ProfileRules.rename("  ", "ana", emptySet()))
    }

    @Test
    fun `PM3 name of 21 code points is invalid and 20 is accepted`() {
        assertEquals(
            RenameOutcome.Invalid(PlayerListError.NAME_TOO_LONG),
            ProfileRules.rename("a".repeat(21), "ana", emptySet()),
        )
        assertTrue(ProfileRules.rename("a".repeat(20), "ana", emptySet()) is RenameOutcome.Renamed)
    }

    @Test
    fun `PM3 case-only rename keeps the key and changes the spelling`() {
        assertEquals(RenameOutcome.Renamed("ANA", "ana"), ProfileRules.rename("ANA", "ana", setOf("bob")))
    }

    @Test
    fun `PM3 key of another profile clashes`() {
        assertEquals(RenameOutcome.Clash("bob"), ProfileRules.rename("Bob", "ana", setOf("bob")))
    }

    @Test
    fun `PM3 surrounding spaces are trimmed`() {
        assertEquals(RenameOutcome.Renamed("Carl", "carl"), ProfileRules.rename("  Carl  ", "ana", setOf("bob")))
    }

    @Test
    fun `PM7 anonymous key is empty`() {
        assertEquals("", ProfileRules.ANONYMOUS_NAME_KEY)
    }

    @Test
    fun `PM4 disjoint sessions can merge`() {
        assertTrue(ProfileRules.canMerge(setOf(1, 2), setOf(3, 4)))
    }

    @Test
    fun `PM4 one shared session refuses the merge`() {
        assertFalse(ProfileRules.canMerge(setOf(1, 2), setOf(2, 4)))
    }

    @Test
    fun `PM4 merged dates take latest reset and last used and earliest creation`() {
        val source = ProfileDates(resetAt = 10, createdAt = 1, lastUsedAt = 50)
        val target = ProfileDates(resetAt = 20, createdAt = 5, lastUsedAt = 40)
        val expected = ProfileDates(resetAt = 20, createdAt = 1, lastUsedAt = 50)
        assertEquals(expected, ProfileRules.mergedDates(source, target))
    }

    @Test
    fun `PM6 solo match is deleted`() {
        assertEquals(DeletionPlan(listOf(1), emptyList()), ProfileRules.deletionPlan(7, 99, mapOf(1L to listOf(7L))))
    }

    @Test
    fun `PM6 match with another player is reassigned`() {
        assertEquals(
            DeletionPlan(emptyList(), listOf(1)),
            ProfileRules.deletionPlan(7, 99, mapOf(1L to listOf(7L, 8L))),
        )
    }

    @Test
    fun `PM6 match with the anonymous profile only is deleted`() {
        assertEquals(
            DeletionPlan(listOf(1), emptyList()),
            ProfileRules.deletionPlan(7, 99, mapOf(1L to listOf(7L, 99L))),
        )
    }

    @Test
    fun `PM6 match with the anonymous profile and a third player is reassigned`() {
        assertEquals(
            DeletionPlan(emptyList(), listOf(1)),
            ProfileRules.deletionPlan(7, 99, mapOf(1L to listOf(7L, 99L, 8L))),
        )
    }

    @Test
    fun `PM6 matches without the profile are ignored and no anonymous profile yet is accepted`() {
        val plan = ProfileRules.deletionPlan(7, null, mapOf(1L to listOf(8L), 2L to listOf(7L)))
        assertEquals(DeletionPlan(listOf(2), emptyList()), plan)
    }

    @Test
    fun `PM8 saved game name with another case locks`() {
        assertTrue(ProfileRules.isLocked(setOf("ana"), listOf("Bob", "ANA")))
    }

    @Test
    fun `PM8 no saved game never locks`() {
        assertFalse(ProfileRules.isLocked(setOf("ana"), emptyList()))
    }

    @Test
    fun `PM8 unrelated saved game does not lock`() {
        assertFalse(ProfileRules.isLocked(setOf("ana"), listOf("Bob")))
    }
}
