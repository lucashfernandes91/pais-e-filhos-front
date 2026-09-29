package com.example.chatapp

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun custodyBelongsToExplicitHolderInsteadOfChildCreator() {
        val child = Child(
            id = 1,
            name = "Clara",
            birth_date = "2021-05-26",
            has_custody = true,
            custody_holder_name = "Jessica",
            conversation = 10,
            created_by_name = "Lucas"
        )

        assertTrue(child.isUnderCustodyOf("jessica"))
        assertFalse(child.isUnderCustodyOf("Lucas"))
    }

    @Test
    fun custodyFallsBackToCreatorForOlderApiResponses() {
        val child = Child(
            id = 1,
            name = "Clara",
            birth_date = "2021-05-26",
            has_custody = true,
            conversation = 10,
            created_by_name = "Lucas"
        )

        assertTrue(child.isUnderCustodyOf("Lucas"))
        assertFalse(child.isUnderCustodyOf("Jessica"))
    }

    @Test
    fun apiDatesHonorTimezoneOffsetsAndNormalizeFractions() {
        val withOffset = AppDateTime.parseApi("2026-09-28T12:00:00-03:00")
        val withMicroseconds = AppDateTime.parseApi("2026-09-28T12:00:00.123456Z")

        assertNotNull(withOffset)
        assertNotNull(withMicroseconds)
        assertEquals(
            AppDateTime.parseApi("2026-09-28T15:00:00Z"),
            withOffset
        )
    }

    @Test
    fun invalidApiDateIsRejected() {
        assertNull(AppDateTime.parseApi("2026-99-99T99:99:99"))
    }
}
