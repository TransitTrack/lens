package eu.transittrack.bench

import java.time.Instant
import java.util.function.Supplier

import com.google.transit.realtime.GtfsRealtime.FeedEntity
import com.google.transit.realtime.GtfsRealtime.FeedHeader
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.google.transit.realtime.GtfsRealtime.Position
import com.google.transit.realtime.GtfsRealtime.TripDescriptor
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor
import com.google.transit.realtime.GtfsRealtime.VehiclePosition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import eu.transittrack.Application
import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.feed.AvlFeedSource
import eu.transittrack.avl.feed.RawAvlPayload
import eu.transittrack.avl.ingest.AvlIngestService
import eu.transittrack.avl.ingest.AvlWriter
import eu.transittrack.avl.match.AvlMatchProcessor
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.predict.PredictionProcessor

/** Swapped in for [eu.transittrack.avl.feed.HttpAvlFeedSource] so each benchmark invocation can hand
 * the poller a freshly-built payload without a real HTTP round trip. Defaults to a valid, empty
 * `FeedMessage` (not an empty byte array) so an unexpected `fetch()` - e.g. from `AvlPoller`'s own
 * background loop, see [PipelineBenchmarkEnvironment.poisonBackgroundSchedulerLocks] - decodes to
 * zero vehicles instead of throwing. */
class StubAvlFeedSource : AvlFeedSource {
    @Volatile var payload: RawAvlPayload = RawAvlPayload(emptyFeedMessageBytes(), null, Instant.now())

    override fun fetch(feed: AvlFeed): RawAvlPayload = payload

    companion object {
        private fun emptyFeedMessageBytes(): ByteArray =
            FeedMessage
                .newBuilder()
                .setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(0))
                .build()
                .toByteArray()
    }
}

/**
 * Boots one full Spring context (ingest/match/predict services, Testcontainers Postgres, the
 * derived-schedule pipeline) around [BenchmarkGtfsFixture]'s synthetic schedule. One instance is
 * created per JMH `@State(Scope.Benchmark)` class's `@Setup(Level.Trial)` - JMH forks a fresh JVM
 * per benchmark method by default, so there is no cross-benchmark sharing to worry about; each
 * fork pays its own (one-time) container + context startup cost, which is excluded from every
 * `@Benchmark`-measured invocation.
 *
 * `AvlSilentVehicleSweeper` and the GTFS re-ingest sweep are pushed out to effectively-never-fire
 * intervals via property overrides. `AvlPoller`, `AvlMatchProcessor` and `PredictionProcessor` are
 * handled differently: their per-feed background loop only starts once their `FeedLockCoordinator`
 * grants them ownership of a feed id, so [poisonBackgroundSchedulerLocks] pre-claims that same
 * ShedLock row for this environment's feed - `processBatch()` itself (what every `@Benchmark`
 * method here actually calls) doesn't check ownership, only the scheduled per-feed loop does, so
 * this blocks exactly the interference and nothing else. A property-only "make the interval huge"
 * approach doesn't work: Spring's `scheduleWithFixedDelay(task, Duration)` always runs its first
 * execution immediately regardless of the delay.
 */
class PipelineBenchmarkEnvironment {
    private lateinit var container: PostgreSQLContainer
    lateinit var ctx: ConfigurableApplicationContext
        private set
    lateinit var feed: AvlFeed
        private set
    lateinit var trips: List<BenchTrip>
        private set
    lateinit var avlFeedSource: StubAvlFeedSource
        private set
    private lateinit var jdbc: JdbcTemplate
    private var started = false

    fun <T : Any> bean(type: Class<T>): T = ctx.getBean(type)

    /** JMH's generated `tryInit()` can invoke a `@Setup(Level.Trial)` method more than once for
     * the same state instance; `start()` must be idempotent rather than double-boot a second
     * Postgres container/Spring context (and fail on the resulting duplicate feed row). */
    fun start() {
        if (started) return
        started = true
        container = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).apply { start() }
        val fixture = BenchmarkGtfsFixture.generate()
        trips = fixture.trips
        val stubSource = StubAvlFeedSource()

        ctx =
            SpringApplicationBuilder(Application::class.java)
                .web(WebApplicationType.NONE)
                .properties(
                    mapOf(
                        "spring.datasource.url" to container.jdbcUrl,
                        "spring.datasource.username" to container.username,
                        "spring.datasource.password" to container.password,
                        "spring.datasource.hikari.maximum-pool-size" to "8",
                        "transittrack.avl.enabled" to "true",
                        "transittrack.predict.enabled" to "true",
                        "transittrack.feed.feeds" to "",
                        "transittrack.gtfs.polling.enabled" to "false",
                        "transittrack.avl.match.match-interval-ms" to "3600000",
                        "transittrack.predict.run.interval-ms" to "3600000",
                        "transittrack.observability.gauge-refresh-ms" to "3600000",
                        "logging.level.root" to "warn",
                        "logging.level.eu.transittrack" to "warn",
                    ),
                ).initializers(
                    ApplicationContextInitializer<ConfigurableApplicationContext> { context ->
                        val gac = context as GenericApplicationContext
                        gac.registerBean(
                            "benchmarkFeedDownloader",
                            FeedDownloader::class.java,
                            Supplier { BenchmarkFeedDownloader(fixture.files) },
                            BeanDefinitionCustomizer { it.isPrimary = true },
                        )
                        gac.registerBean(
                            "benchmarkAvlFeedSource",
                            AvlFeedSource::class.java,
                            Supplier { stubSource },
                            BeanDefinitionCustomizer { it.isPrimary = true },
                        )
                    },
                ).run()
        avlFeedSource = stubSource
        jdbc = bean(JdbcTemplate::class.java)

        // `transittrack.feed.feeds` is a YAML-bound List<FeedDef>; Spring's relaxed binder doesn't
        // let a single scalar property override an indexed list from a lower-priority source, so
        // `"transittrack.feed.feeds" to ""` above does NOT stop GtfsFeedConfigSynchronizer from
        // seeding the real poznan/stpt/otl/wroclaw feeds from src/main/resources/application.yaml
        // at startup. Delete whatever it (and AvlFeedConfigSynchronizer) seeded before creating our
        // own feed - also makes `start()` safe if JMH ever invokes Level.Trial setup more than once
        // for the same state instance, which it has been observed to do.
        //
        // GtfsFeedConfigSynchronizer kicks off each config feed's ingest on its own background
        // thread (`gtfs-ingest-N`) before returning control here, so deleting out from under it
        // races that thread - expect (harmless) "No value present" stack traces logged from
        // IngestionService/RevisionService on startup; they're isolated to that background thread
        // and don't affect this environment's own feed or any `@Benchmark`-measured call.
        jdbc.execute("delete from avl_feed")
        jdbc.execute("delete from gtfs_feed")

        bean(GtfsFeedService::class.java).register(FeedInput("bench-gtfs", "Bench", null, "bench://fixed", null))
        bean(IngestionService::class.java).ingestBlocking("bench-gtfs")

        val now = Instant.now()
        feed =
            bean(AvlFeedRepository::class.java).save(
                AvlFeed(
                    code = "bench", name = "Bench", gtfsFeedCode = "bench-gtfs", url = "bench://fixed",
                    format = AvlFormat.GTFS_RT, pollIntervalSec = 999_999,
                    assignmentMode = AvlAssignmentMode.DESCRIPTOR_THEN_INFER,
                    enabled = true, headers = null, source = AvlFeedSourceKind.CONFIG,
                    createdAt = now, updatedAt = now,
                ),
            )
        poisonBackgroundSchedulerLocks(feed.id!!)
    }

    /** Pre-claims the ShedLock rows `AvlPoller`/`AvlMatchProcessor` (`avl-feed:<id>`) and
     * `PredictionProcessor` (`predictor-feed:<id>`) would otherwise acquire for this feed - see the
     * class doc. `lock_until` ten years out easily outlives any single benchmark run. */
    private fun poisonBackgroundSchedulerLocks(feedId: Long) {
        val lockUntil = Instant.now().plusSeconds(10L * 365 * 24 * 3600)
        for (lockName in listOf("avl-feed:$feedId", "predictor-feed:$feedId")) {
            jdbc.update(
                "insert into shedlock (name, lock_until, locked_at, locked_by) values (?, ?, now(), 'benchmark')",
                lockName, java.sql.Timestamp.from(lockUntil),
            )
        }
    }

    fun stop() {
        runCatching { ctx.close() }
        runCatching { container.stop() }
    }

    /** Clears the mutable AVL tables between benchmark invocations so table growth and dedup /
     * match-status state don't skew later iterations - cheap, and unmeasured (runs in `@Setup`). */
    fun resetAvlTables() {
        jdbc.execute("truncate table avl_report, vehicle_match, vehicle_state restart identity cascade")
    }

    /** One in three descriptors resolve directly (the `TrustDescriptorMatcher` fast path), one in
     * three carry only a route (forcing its route+time disambiguation), one in three carry neither
     * (forcing a `FullInferenceMatcher` fallback) - a mix reflecting the poznan/stpt/wroclaw
     * descriptor-reliability spread observed in production this session. */
    private fun descriptorMode(i: Int) = i % 3

    /** Builds a synthetic GTFS-RT `FeedMessage` for [vehicleCount] vehicles, cycling through the
     * fixture's trips and placing each vehicle exactly on one of that trip's shape points. */
    fun gtfsRtPayload(
        vehicleCount: Int,
        epochSec: Long,
    ): ByteArray {
        val builder = FeedMessage.newBuilder().setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(epochSec))
        for (i in 0 until vehicleCount) {
            val trip = trips[i % trips.size]
            val (lat, lon) = trip.points[i % trip.points.size]
            val vp =
                VehiclePosition
                    .newBuilder()
                    .setVehicle(VehicleDescriptor.newBuilder().setId("veh-$i"))
                    .setPosition(Position.newBuilder().setLatitude(lat.toFloat()).setLongitude(lon.toFloat()))
                    .setTimestamp(epochSec)
            when (descriptorMode(i)) {
                0 -> {
                    vp.trip = TripDescriptor.newBuilder().setTripId(trip.tripId).build()
                }

                1 -> {
                    vp.trip = TripDescriptor.newBuilder().setRouteId(trip.routeId).build()
                }

                else -> {}
            }
            builder.addEntity(FeedEntity.newBuilder().setId("e$i").setVehicle(vp))
        }
        return builder.build().toByteArray()
    }

    /** Directly inserts [vehicleCount] PENDING `avl_report` rows, bypassing decode/ingest, to
     * isolate the matching stage's own cost from the ingest stage's. */
    fun seedPendingAvlReports(
        vehicleCount: Int,
        at: Instant,
    ) {
        val rows =
            (0 until vehicleCount).map { i ->
                val trip = trips[i % trips.size]
                val (lat, lon) = trip.points[i % trip.points.size]
                AvlReportRow(
                    feedId = feed.id!!,
                    vehicleId = "veh-$i",
                    vehicleLabel = null,
                    ts = at,
                    lat = lat,
                    lon = lon,
                    bearing = null,
                    speedMps = null,
                    odometerM = null,
                    descTripId = if (descriptorMode(i) == 0) trip.tripId else null,
                    descRouteId = if (descriptorMode(i) <= 1) trip.routeId else null,
                    descDirectionId = null,
                    descStartDate = null,
                    descStartTimeSec = null,
                    descScheduleRelationship = null,
                    currentStopSequence = null,
                    currentStopId = null,
                    currentStatus = null,
                    occupancyStatus = null,
                    congestionLevel = null,
                    matchStatus = MatchStatus.PENDING,
                    matchedAt = null,
                    createdAt = at,
                )
            }
        bean(AvlWriter::class.java).insertReports(rows)
    }

    /** Seeds PENDING `avl_report` rows and runs them through matching, so `vehicle_match` carries
     * [vehicleCount] fresh PENDING rows for the prediction benchmark to claim - matching's own cost
     * is paid here, in unmeasured `@Setup` time. */
    fun seedPendingVehicleMatches(
        vehicleCount: Int,
        at: Instant,
    ) {
        seedPendingAvlReports(vehicleCount, at)
        bean(AvlMatchProcessor::class.java).processBatch()
    }

    fun ingestService(): AvlIngestService = bean(AvlIngestService::class.java)

    fun matchProcessor(): AvlMatchProcessor = bean(AvlMatchProcessor::class.java)

    fun predictionProcessor(): PredictionProcessor = bean(PredictionProcessor::class.java)
}
