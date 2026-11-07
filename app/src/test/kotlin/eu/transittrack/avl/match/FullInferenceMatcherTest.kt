package eu.transittrack.avl.match

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.ingest.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleState
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.schedule.model.TripRef
import eu.transittrack.support.PostgresPerClassTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(FullInferenceMatcherTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullInferenceMatcherTest(
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val trips: TripRepository,
    @Autowired val matcher: FullInferenceMatcher,
    @Autowired val spatial: SpatialMatcher,
    @Autowired val scorer: MatchScorer,
    @Autowired val props: AvlProperties,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @BeforeAll
    fun ingest() {
        truncateBeforeFixture()
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
    }

    private val avlFeed =
        AvlFeed(
            code = "a",
            name = "A",
            gtfsFeedCode = "g",
            url = "x",
            format = AvlFormat.GTFS_RT,
            pollIntervalSec = 15,
            assignmentMode = AvlAssignmentMode.FULL_INFERENCE,
            enabled = true,
            headers = null,
            source = AvlFeedSourceKind.CONFIG,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private fun report(
        lat: Double,
        lon: Double,
        ts: Instant,
        descRouteId: String? = null,
    ): AvlReportView =
        AvlReportRow(
            id = 1L,
            feedCode = "f1",
            vehicleId = "v",
            vehicleLabel = null,
            ts = ts,
            lat = lat,
            lon = lon,
            bearing = null,
            speedMps = null,
            odometerM = null,
            descTripId = null,
            descRouteId = descRouteId,
            descDirectionId = null,
            descStartDate = LocalDate.of(2026, 9, 7),
            descStartTimeSec = null,
            descScheduleRelationship = null,
            currentStopSequence = null,
            currentStopId = null,
            currentStatus = null,
            occupancyStatus = null,
            congestionLevel = null,
            matchStatus = MatchStatus.PENDING,
            matchedAt = null,
            createdAt = Instant.EPOCH,
        )

    private fun prevOnTrip(
        tripRowId: Long,
        alongM: Double,
        consecutiveFailures: Int = 0,
    ): VehicleStateView =
        VehicleState(
            feedCode = "f1",
            vehicleId = "v",
            vehicleLabel = null,
            reportTs = Instant.EPOCH,
            lat = 0.0,
            lon = 0.0,
            bearing = null,
            speedMps = null,
            occupancyStatus = null,
            matched = true,
            stale = false,
            consecutiveFailures = consecutiveFailures,
            revisionId = null,
            tripRowId = tripRowId,
            blockPk = null,
            tripPatternId = null,
            stopPathIndex = null,
            distanceAlongTripM = alongM,
            scheduleAdherenceSec = null,
            snappedLat = null,
            snappedLon = null,
            updatedAt = Instant.EPOCH,
        )

    private fun ts0805() =
        LocalDate
            .of(2026, 9, 7)
            .atTime(8, 5)
            .atZone(factory.open(avlFeed)!!.zone)
            .toInstant()

    @Test
    fun `on-shape T1 midpoint with no prev matches T1`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val mid = geom.line.pointAt(geom.line.lengthM / 2.0)

        val outcome = matcher.match(report(mid.lat, mid.lon, ts0805()), prev = null, ctx)

        assertThat(outcome).isInstanceOf(MatchOutcome.Matched::class)
        assertThat((outcome as MatchOutcome.Matched).tripRowId).isEqualTo(trip.id)
    }

    @Test
    fun `advancing along T1 stays matched with larger distance`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val len = geom.line.lengthM
        val p = geom.line.pointAt(0.6 * len)

        val outcome = matcher.match(report(p.lat, p.lon, ts0805()), prevOnTrip(trip.id!!, 0.4 * len), ctx)

        assertThat(outcome).isInstanceOf(MatchOutcome.Matched::class)
        val m = outcome as MatchOutcome.Matched
        assertThat(m.tripRowId).isEqualTo(trip.id)
        assertThat(m.distanceAlongTripM > 0.4 * len).isTrue()
    }

    private fun at(
        hour: Int,
        minute: Int,
    ) = LocalDate
        .of(2026, 9, 7)
        .atTime(hour, minute)
        .atZone(factory.open(avlFeed)!!.zone)
        .toInstant()

    @Test
    fun `vehicle still at the first stop long after departure drops the trip it never started`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val start = ctx.patternGeometry(trip.tripPatternId!!)!!.line.pointAt(0.0)

        // T1 leaves S1 at 08:00; at 08:15 the vehicle is still standing there.
        val outcome = matcher.match(report(start.lat, start.lon, at(8, 15)), prevOnTrip(trip.id!!, 0.0), ctx)

        assertThat((outcome as? MatchOutcome.Matched)?.tripRowId).isNotEqualTo(trip.id)
    }

    @Test
    fun `vehicle at the first stop shortly after departure keeps the trip`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val start = ctx.patternGeometry(trip.tripPatternId!!)!!.line.pointAt(0.0)

        val outcome = matcher.match(report(start.lat, start.lon, at(8, 5)), prevOnTrip(trip.id!!, 0.0), ctx)

        assertThat(outcome).isInstanceOf(MatchOutcome.Matched::class)
        assertThat((outcome as MatchOutcome.Matched).tripRowId).isEqualTo(trip.id)
    }

    @Test
    fun `vehicle that left the first stop late stays on its trip`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val p = geom.line.pointAt(0.3 * geom.line.lengthM)

        val outcome = matcher.match(report(p.lat, p.lon, at(8, 20)), prevOnTrip(trip.id!!, 0.2 * geom.line.lengthM), ctx)

        assertThat(outcome).isInstanceOf(MatchOutcome.Matched::class)
        assertThat((outcome as MatchOutcome.Matched).tripRowId).isEqualTo(trip.id)
    }

    @Test
    fun `vehicle at the first stop well before departure isn't matched to that trip yet`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val start = ctx.patternGeometry(trip.tripPatternId!!)!!.line.pointAt(0.0)

        // T1 leaves S1 at 08:00; 15 minutes before is outside the 10-minute lead.
        val outcome = matcher.match(report(start.lat, start.lon, at(7, 45)), prev = null, ctx)

        assertThat((outcome as? MatchOutcome.Matched)?.tripRowId).isNotEqualTo(trip.id)
    }

    @Test
    fun `vehicle at the first stop shortly before departure is matched to that trip`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val start = ctx.patternGeometry(trip.tripPatternId!!)!!.line.pointAt(0.0)

        val outcome = matcher.match(report(start.lat, start.lon, at(7, 52)), prev = null, ctx)

        assertThat(outcome).isInstanceOf(MatchOutcome.Matched::class)
        assertThat((outcome as MatchOutcome.Matched).tripRowId).isEqualTo(trip.id)
    }

    @Test
    fun `vehicle still standing at the terminus after the trip ended drops the finished trip`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val end = geom.line.pointAt(geom.line.lengthM)

        // T1 reaches S4 at 08:30; at 08:45 the vehicle already there on T1 is still standing at S4.
        val outcome = matcher.match(report(end.lat, end.lon, at(8, 45)), prevOnTrip(trip.id!!, geom.line.lengthM), ctx)

        assertThat((outcome as? MatchOutcome.Matched)?.tripRowId).isNotEqualTo(trip.id)
    }

    @Test
    fun `vehicle found at the terminus after the trip ended isn't put on the finished trip`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val end = geom.line.pointAt(geom.line.lengthM)

        val outcome = matcher.match(report(end.lat, end.lon, at(8, 45)), prev = null, ctx)

        assertThat((outcome as? MatchOutcome.Matched)?.tripRowId).isNotEqualTo(trip.id)
    }

    @Test
    fun `vehicle arriving late at the terminus still matches its trip`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val end = geom.line.pointAt(geom.line.lengthM)

        // Running T1 15 minutes late: the arrival at S4 itself must still count.
        val outcome = matcher.match(report(end.lat, end.lon, at(8, 45)), prevOnTrip(trip.id!!, 0.9 * geom.line.lengthM), ctx)

        assertThat(outcome).isInstanceOf(MatchOutcome.Matched::class)
        assertThat((outcome as MatchOutcome.Matched).tripRowId).isEqualTo(trip.id)
    }

    @Test
    fun `garbage position with no prev fails`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val mid = geom.line.pointAt(geom.line.lengthM / 2.0)

        assertThat(matcher.match(report(mid.lat + 0.05, mid.lon + 0.05, ts0805()), prev = null, ctx))
            .isEqualTo(MatchOutcome.Failed)
    }

    /**
     * [ctx] plus a copy of T1 on another route ("RB"), running the same pattern and schedule — two
     * routes sharing a corridor. Listed first among the candidates, so it wins a tied score.
     */
    private fun withSharedCorridor(
        ctx: MatchContext,
        t1: TripRef,
    ): Pair<MatchContext, TripRef> {
        val other = t1.copy(id = 999_001L, tripId = "X1", routeId = "RB", blockId = null)
        val shared =
            object : MatchContext by ctx {
                override fun trip(tripRowId: Long) = if (tripRowId == other.id) other else ctx.trip(tripRowId)

                override fun scheduleOf(tripRowId: Long) = ctx.scheduleOf(if (tripRowId == other.id) t1.id else tripRowId)

                override fun blockTripOf(tripRowId: Long) = if (tripRowId == other.id) null else ctx.blockTripOf(tripRowId)

                override fun candidateTrips(serviceIds: Set<String>) = listOf(other) + ctx.candidateTrips(serviceIds)

                override fun candidateTripsForRoute(
                    routeId: String,
                    serviceIds: Set<String>,
                ) = if (routeId == other.routeId) listOf(other) else ctx.candidateTripsForRoute(routeId, serviceIds)
            }
        return shared to other
    }

    private fun matcherReleasingRouteAfter(failures: Int) =
        FullInferenceMatcher(
            spatial,
            scorer,
            props.copy(match = props.match.copy(releaseDescriptorRouteAfterFailures = failures)),
        )

    @Test
    fun `descriptor route keeps a vehicle off another route's trip on a shared corridor`() {
        val base = factory.open(avlFeed)!!
        val t1 = base.trip(trips.findByTripId(base.revisionId, "T1")!!.id!!)!!
        val (ctx, other) = withSharedCorridor(base, t1)
        val geom = ctx.patternGeometry(t1.tripPatternId!!)!!
        val p = geom.line.pointAt(0.5 * geom.line.lengthM)

        // Continuity and hysteresis would keep the vehicle on route RB; its descriptor says RA.
        val outcome = matcher.match(report(p.lat, p.lon, ts0805(), descRouteId = "RA"), prevOnTrip(other.id, 0.4 * geom.line.lengthM), ctx)

        assertThat(outcome).isInstanceOf(MatchOutcome.Matched::class)
        assertThat((outcome as MatchOutcome.Matched).tripRowId).isEqualTo(t1.id)
    }

    @Test
    fun `report matching no trip of its descriptor route fails instead of taking another route`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val mid = geom.line.pointAt(geom.line.lengthM / 2.0)

        assertThat(
            matcher
                .match(report(mid.lat, mid.lon, ts0805(), descRouteId = "NOPE"), prevOnTrip(trip.id!!, 0.4 * geom.line.lengthM, 4), ctx),
        ).isEqualTo(MatchOutcome.Failed)
    }

    @Test
    fun `descriptor route is released only after the configured number of failures`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val mid = geom.line.pointAt(geom.line.lengthM / 2.0)
        val releasing = matcherReleasingRouteAfter(5)
        val bogusRoute = report(mid.lat, mid.lon, ts0805(), descRouteId = "NOPE")

        assertThat(releasing.match(bogusRoute, prevOnTrip(trip.id!!, 0.4 * geom.line.lengthM, 4), ctx))
            .isEqualTo(MatchOutcome.Failed)

        val released = releasing.match(bogusRoute, prevOnTrip(trip.id!!, 0.4 * geom.line.lengthM, 5), ctx)
        assertThat(released).isInstanceOf(MatchOutcome.Matched::class)
        assertThat((released as MatchOutcome.Matched).tripRowId).isEqualTo(trip.id)
    }

    @Test
    fun `another trip on the previous trip's route and direction beats a tied trip on another route`() {
        val base = factory.open(avlFeed)!!
        val t1 = base.trip(trips.findByTripId(base.revisionId, "T1")!!.id!!)!!
        val t2 = trips.findByTripId(base.revisionId, "T2")!!
        val (ctx, _) = withSharedCorridor(base, t1)
        val geom = ctx.patternGeometry(t1.tripPatternId!!)!!
        val p = geom.line.pointAt(0.5 * geom.line.lengthM)

        // prev ran T2 (route RA, direction 0, its own block) - no longer a candidate at 08:05. T1 and
        // its RB copy tie on everything else; route continuity has to break the tie towards RA.
        val outcome = matcher.match(report(p.lat, p.lon, ts0805()), prevOnTrip(t2.id!!, 0.0), ctx)

        assertThat(outcome).isInstanceOf(MatchOutcome.Matched::class)
        assertThat((outcome as MatchOutcome.Matched).tripRowId).isEqualTo(t1.id)
    }
}
