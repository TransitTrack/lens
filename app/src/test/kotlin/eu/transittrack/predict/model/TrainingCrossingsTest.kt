package eu.transittrack.predict.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull

class TrainingCrossingsTest {
    @Test
    fun growsPastItsInitialCapacityAndKeepsEveryColumn() {
        val at = Instant.parse("2026-09-01T10:00:00Z")
        val builder = TrainingCrossings.Builder(initialCapacity = 2)
        for (i in 0 until 100) {
            builder.add(
                tripPatternId = i.toLong(),
                revisionId = 1_000L + i,
                stopPathIndex = i % 7,
                observedAt = at.plusMillis(i.toLong()),
                travelSec = i + 0.5,
                tripStartSec = if (i % 2 == 0) i * 60 else null,
                directionId = if (i % 3 == 0) null else i % 2,
                routeId = if (i % 5 == 0) null else "R${i % 4}",
            )
        }
        val rows = builder.build()

        assertThat(rows.size).isEqualTo(100)
        for (i in 0 until 100) {
            assertThat(rows.tripPatternId(i)).isEqualTo(i.toLong())
            assertThat(rows.revisionId(i)).isEqualTo(1_000L + i)
            assertThat(rows.stopPathIndex(i)).isEqualTo(i % 7)
            assertThat(rows.observedAt(i)).isEqualTo(at.plusMillis(i.toLong()))
            assertThat(rows.travelSec(i)).isEqualTo(i + 0.5)
            assertThat(rows.tripStartSec(i)).isEqualTo(if (i % 2 == 0) i * 60 else null)
            assertThat(rows.directionId(i)).isEqualTo(if (i % 3 == 0) null else i % 2)
            assertThat(rows.routeId(i)).isEqualTo(if (i % 5 == 0) null else "R${i % 4}")
        }
    }

    @Test
    fun indexPastSizeThrows() {
        val rows = TrainingCrossings.Builder(initialCapacity = 8).build()
        assertThat(rows.size).isEqualTo(0)
        assertFailsWith<IndexOutOfBoundsException> { rows.travelSec(0) }
        assertThat(TrainingCrossings.Builder().build().size).isEqualTo(0)
    }

    @Test
    fun zeroTripStartIsKept() {
        val b = TrainingCrossings.Builder()
        b.add(1, 1, 0, Instant.EPOCH, 0.0, 0, 0, "R")
        b.add(1, 1, 0, Instant.EPOCH, 0.0, null, null, null)
        val rows = b.build()
        assertThat(rows.tripStartSec(0)).isEqualTo(0)
        assertThat(rows.directionId(0)).isEqualTo(0)
        assertThat(rows.tripStartSec(1)).isNull()
    }
}
