package eu.transittrack.schedule.compare.api

import kotlin.test.Test

import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean

import eu.transittrack.gtfs.api.GraphQlConfiguration
import eu.transittrack.schedule.compare.ScheduleComparison
import eu.transittrack.schedule.compare.ScheduleComparisonService
import eu.transittrack.schedule.compare.StopChangeKind
import eu.transittrack.schedule.compare.StopTimeChange
import eu.transittrack.schedule.compare.TripChange
import eu.transittrack.schedule.compare.TripChangeKind

@GraphQlTest(controllers = [ScheduleComparisonController::class])
@Import(GraphQlConfiguration::class)
class ScheduleComparisonGraphQlTest(
    @Autowired val tester: GraphQlTester,
) {
    @MockitoBean lateinit var service: ScheduleComparisonService

    @Test
    fun `compareRevisions returns trip and calendar changes with pagination fields`() {
        val comparison =
            ScheduleComparison(
                fromRevisionId = 10,
                toRevisionId = 11,
                fromDerivationStale = false,
                toDerivationStale = false,
                tripChanges =
                    listOf(
                        TripChange(
                            tripId = "T1",
                            kind = TripChangeKind.MODIFIED,
                            fieldChanges = mapOf("routeId" to ("R1" to "R2")),
                            stopChanges =
                                listOf(
                                    StopTimeChange(1, StopChangeKind.STOP_TIME_CHANGED, "S1", 60, 60),
                                ),
                            runTimeDeltaSec = 60,
                        ),
                    ),
                calendarChanges = emptyList(),
                headwaySummaries = emptyList(),
            )
        whenever(service.compare(any(), any(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(comparison)

        tester
            .document(
                """
                query {
                  compareRevisions(fromRevisionId: "10", toRevisionId: "11") {
                    fromRevisionId toRevisionId tripChangeCount
                    tripChanges { tripId kind runTimeDeltaSec stopChanges { stopSequence kind arrivalDeltaSec } }
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("compareRevisions.tripChangeCount")
            .entity(Int::class.java)
            .isEqualTo(1)
            .path("compareRevisions.tripChanges[0].tripId")
            .entity(String::class.java)
            .isEqualTo("T1")
            .path("compareRevisions.tripChanges[0].stopChanges[0].arrivalDeltaSec")
            .entity(Int::class.java)
            .isEqualTo(60)
    }

    @Test
    fun `compareRevisions paginates tripChanges independently of tripChangeCount`() {
        fun tripChange(id: String) =
            TripChange(
                tripId = id,
                kind = TripChangeKind.MODIFIED,
                fieldChanges = emptyMap(),
                stopChanges = emptyList(),
                runTimeDeltaSec = null,
            )
        val comparison =
            ScheduleComparison(
                fromRevisionId = 10,
                toRevisionId = 11,
                fromDerivationStale = false,
                toDerivationStale = false,
                tripChanges = listOf(tripChange("T1"), tripChange("T2"), tripChange("T3")),
                calendarChanges = emptyList(),
                headwaySummaries = emptyList(),
            )
        whenever(service.compare(any(), any(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(comparison)

        tester
            .document(
                """
                query {
                  compareRevisions(fromRevisionId: "10", toRevisionId: "11", limit: 1) {
                    tripChangeCount
                    tripChanges { tripId }
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("compareRevisions.tripChangeCount")
            .entity(Int::class.java)
            .isEqualTo(3)
            .path("compareRevisions.tripChanges")
            .entityList(Any::class.java)
            .hasSize(1)
    }
}
