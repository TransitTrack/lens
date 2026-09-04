package eu.transittrack.avl.ingest

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isNull

class AvlSignalsTest {
    @Test
    fun `AvlReport optional fields default to null`() {
        val r = AvlReport(vehicleId = "v", vehicleLabel = null, ts = Instant.EPOCH, lat = 1.0, lon = 2.0)
        assertThat(r.descTripId).isNull()
        assertThat(r.currentStatus).isNull()
        assertThat(r.occupancyStatus).isNull()
    }
}
