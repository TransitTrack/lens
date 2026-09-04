package eu.transittrack.avl.match

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isBetween

import eu.transittrack.avl.AvlProperties

class MatchScorerTest {
    private val scorer = MatchScorer(AvlProperties())

    private fun near(
        value: Double,
        expected: Double,
    ) = assertThat(value).isBetween(expected - 1e-9, expected + 1e-9)

    @Test
    fun `perfect match scores 1`() {
        near(scorer.score(0.0, 90.0, 90.0, 0, 1.0), 1.0)
    }

    @Test
    fun `worst-ish inputs score 0`() {
        near(scorer.score(60.0, 0.0, 180.0, 3600, 0.0), 0.0)
    }

    @Test
    fun `null bearing zeroes only the heading term`() {
        near(scorer.score(0.0, null, 90.0, 0, 1.0), 0.8)
    }

    @Test
    fun `null adherence gives half schedule term`() {
        near(scorer.score(0.0, 90.0, 90.0, null, 1.0), 0.9)
    }
}
