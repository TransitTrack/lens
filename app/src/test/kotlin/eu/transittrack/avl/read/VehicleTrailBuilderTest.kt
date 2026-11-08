package eu.transittrack.avl.read

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull

import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.PredictionStatus
import eu.transittrack.avl.model.VehicleMatch

class VehicleTrailBuilderTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `summarises track, interval, newest status and current-trip speeds`() {
        // Newest first, as the repository returns them: 3 reports 10 s apart.
        val reports =
            listOf(
                report(3, 20, lat = 3.0, speed = 4.0, status = 1, desc = "TRIP-DECLARED"),
                report(2, 10, lat = 2.0, speed = 8.0),
                report(1, 0, lat = 1.0, speed = 6.0),
            )
        val matches =
            mapOf(
                3L to match(3, trip = 70, distance = 500.0),
                2L to match(2, trip = 70, distance = 200.0),
                1L to match(1, trip = 99, distance = 100.0), // an earlier trip of the same vehicle
            )

        val trail = buildVehicleTrail(reports, matches, currentTripRowId = 70)

        assertThat(trail.reportCount).isEqualTo(3)
        assertThat(trail.track.map { it.lat }).containsExactly(1.0, 2.0, 3.0)
        assertThat(trail.avgReportIntervalSec).isEqualTo(10.0)
        assertThat(trail.latestReportTs).isEqualTo(t0.plusSeconds(20).toString())
        assertThat(trail.descTripId).isEqualTo("TRIP-DECLARED")
        // Only the current trip's reports, ordered by distance along it.
        assertThat(trail.speeds.map { it.distanceAlongTripM }).containsExactly(200.0, 500.0)
        assertThat(trail.speeds.map { it.speedMps }).containsExactly(8.0, 4.0)
    }

    @Test
    fun `leaves out reports without a speed, a match or a finite distance`() {
        val reports = listOf(report(3, 20, speed = null), report(2, 10, speed = 5.0), report(1, 0, speed = 5.0))
        val matches = mapOf(3L to match(3, 70, 300.0), 1L to match(1, 70, Double.NaN))

        val trail = buildVehicleTrail(reports, matches, currentTripRowId = 70)

        assertThat(trail.speeds).isEmpty()
        assertThat(trail.track.size).isEqualTo(3)
    }

    @Test
    fun `an unmatched vehicle has a track but no speed profile`() {
        val reports = listOf(report(2, 10, speed = 5.0), report(1, 0, speed = 5.0))

        val trail = buildVehicleTrail(reports, mapOf(2L to match(2, 70, 10.0)), currentTripRowId = null)

        assertThat(trail.speeds).isEmpty()
        assertThat(trail.track.size).isEqualTo(2)
    }

    @Test
    fun `with fewer than two reports there is no interval, and with none no latest report`() {
        val one = buildVehicleTrail(listOf(report(1, 0)), emptyMap(), null)
        assertThat(one.avgReportIntervalSec).isNull()
        assertThat(one.reportCount).isEqualTo(1)

        val none = buildVehicleTrail(emptyList(), emptyMap(), null)
        assertThat(none.reportCount).isEqualTo(0)
        assertThat(none.latestReportTs).isNull()
        assertThat(none.currentStatus).isNull()
        assertThat(none.track).isEmpty()
    }

    @Test
    fun `an unknown stop status ordinal is dropped rather than failing`() {
        assertThat(buildVehicleTrail(listOf(report(1, 0, status = 99)), emptyMap(), null).currentStatus).isNull()
    }

    private fun report(
        id: Long,
        offsetSec: Long,
        lat: Double = 44.0,
        speed: Double? = null,
        status: Int? = null,
        desc: String? = null,
    ) = AvlReportRow(
        feedCode = "f", vehicleId = "bus", vehicleLabel = null, ts = t0.plusSeconds(offsetSec), lat = lat, lon = 26.0,
        bearing = null, speedMps = speed, odometerM = null, descTripId = desc, descRouteId = null, descDirectionId = null,
        descStartDate = null, descStartTimeSec = null, descScheduleRelationship = null, currentStopSequence = null,
        currentStopId = null, currentStatus = status, occupancyStatus = null, congestionLevel = null,
        matchStatus = MatchStatus.MATCHED, matchedAt = null, createdAt = t0, id = id,
    )

    private fun match(
        reportId: Long,
        trip: Long,
        distance: Double,
    ) = VehicleMatch(
        avlReportId = reportId, feedCode = "f", vehicleId = "bus", ts = t0, revisionId = 7, tripRowId = trip, blockPk = null,
        tripPatternId = null, stopPathIndex = 0, distanceAlongTripM = distance, deviationM = 0.0, scheduleAdherenceSec = null,
        snappedLat = 0.0, snappedLon = 0.0, heading = null, score = null, createdAt = t0,
        predictionStatus = PredictionStatus.PENDING,
    )
}
