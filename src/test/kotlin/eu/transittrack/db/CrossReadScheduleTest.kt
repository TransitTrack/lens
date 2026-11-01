package eu.transittrack.db

import java.time.Instant
import java.util.stream.Stream
import jakarta.persistence.EntityManager
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.Extent
import eu.transittrack.Point
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.model.RevisionScopedTable
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.schedule.model.Block
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.BlockTrip
import eu.transittrack.schedule.model.ScheduleTables
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.TravelTimeSource
import eu.transittrack.schedule.model.TravelTimesForStopPath
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Interim: while JPA entities and Exposed tables describe the same derived schedule rows, each must
 * read what the other wrote, field by field. Per entity one fully populated sample (SMALLINT enums
 * at the edges of their range) and, where it has nullable columns, one with every nullable column
 * null. Deleted with the JPA entities.
 */
@SpringBootTest
class CrossReadScheduleTest(
    @Autowired val em: EntityManager,
    @Autowired val txManager: PlatformTransactionManager,
    @Autowired val exposedDatabase: Database,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val trips: TripRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val blocks: BlockRepository,
) : PostgresPerMethodTest() {
    /** What the samples point at (all FKs): the revision, a trip, a trip pattern and a block. */
    class Fixture(
        val rev: Long,
        val tripRowId: Long,
        val tripPatternId: Long,
        val blockPk: Long,
    )

    private lateinit var fx: Fixture

    @BeforeEach
    fun seed() {
        val feedId =
            feeds
                .save(
                    GtfsFeed(
                        code = "cross-schedule",
                        name = "Cross schedule",
                        description = null,
                        url = "http://x/f.zip",
                        pollingCron = null,
                        enabled = true,
                        autoActivate = null,
                        source = FeedSource.API,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                ).id!!
        val rev = revisions.save(GtfsRevision(feedId = feedId, status = GtfsRevisionStatus.PENDING, sourceUrl = "u")).id!!
        val trip = trips.save(Trip(rev, "R1", "WD", "T1", null, null, null, "B1", null, null, null))
        val pattern = patterns.save(TripPattern(rev, "K", "R1", null, 0, "H", "SH1", 2, null, tripCount = 1))
        val block = blocks.save(Block(rev, "B1", "WD", 0, 3600, 1, listOf("R1")))
        fx = Fixture(rev, trip.id!!, pattern.id!!, block.id!!)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    fun jpaWritesExposedReads(
        name: String,
        sample: (Fixture, String) -> RevisionScoped,
    ) {
        val written = sample(fx, "jpa")
        TransactionTemplate(txManager).executeWithoutResult {
            em.persist(written)
            em.flush()
        }
        val table = tableFor(written)
        val read = transaction(exposedDatabase) { table.read(table.selectAll().where { table.id eq written.id!! }.single()) }
        assertSameFields(written, read)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    fun exposedWritesJpaReads(
        name: String,
        sample: (Fixture, String) -> RevisionScoped,
    ) {
        val written = sample(fx, "exposed")
        val table = tableFor(written)
        transaction(exposedDatabase) {
            @Suppress("UNCHECKED_CAST")
            val t = table as RevisionScopedTable<RevisionScoped>
            written.id = t.insert { t.bind(it, written) }[t.id]
        }
        val read = TransactionTemplate(txManager).execute { em.find(written.javaClass, written.id!!) }
        assertThat(read, name = "JPA row").isNotNull()
        assertSameFields(written, read!!)
    }

    private fun tableFor(row: RevisionScoped): RevisionScopedTable<*> = ScheduleTables.ALL.single { it.rowClass == row.javaClass }

    /** Every stored property (those with a backing field, inherited `id`/`revisionId` included). */
    private fun assertSameFields(
        expected: RevisionScoped,
        actual: RevisionScoped,
    ) {
        assertThat(actual.javaClass).isEqualTo(expected.javaClass)
        for (p in expected::class.memberProperties) {
            if (p.javaField == null) continue
            val e = p.getter.call(expected)
            val a = p.getter.call(actual)
            if (e is Extent || a is Extent) {
                assertThat(a?.let(::extentFields), name = p.name).isEqualTo(e?.let(::extentFields))
            } else {
                assertThat(a, name = p.name).isEqualTo(e)
            }
        }
    }

    private fun extentFields(e: Any): List<Double> = (e as Extent).let { listOf(it.minLat, it.maxLat, it.minLon, it.maxLon) }

    companion object {
        private const val SMALL_MIN = Short.MIN_VALUE.toInt()
        private const val SMALL_MAX = Short.MAX_VALUE.toInt()

        private fun extent() = Extent.of(listOf(Point(45.70, 21.15), Point(45.80, 21.30)))

        private fun s(
            name: String,
            f: (Fixture, String) -> RevisionScoped,
        ): Arguments = Arguments.of(name, f)

        @JvmStatic
        fun samples(): Stream<Arguments> =
            Stream.of(
                s("TripPattern") { r, k -> TripPattern(r.rev, "K-$k", "R1", "33", SMALL_MAX, "Head", "SH1", 5, 1234.5, extent(), 7) },
                // An unset Extent() is NaN in all four bounds, which is what derivation stores for a pattern without coordinates.
                s("TripPattern (nulls)") { r, k -> TripPattern(r.rev, "K-$k", "R1", null, null, null, null, 0, null, Extent(), 0) },
                s("StopPath") { r, _ ->
                    StopPath(
                        r.rev, r.tripPatternId, 3, "S1", "R1", 4, 250.5,
                        // Postgres normal form for jsonb text (keys ordered by length, then bytes).
                        """{"type": "LineString", "coordinates": [[21.2, 45.7], [21.3, 45.8]]}""",
                        SMALL_MAX, SMALL_MIN, true, false, true, 300,
                    )
                },
                s("StopPath (nulls)") { r, _ ->
                    StopPath(r.rev, r.tripPatternId, 3, "S1", null, 4, 0.0, null, null, null, false, true, false, null)
                },
                s("ScheduleTime") { r, _ -> ScheduleTime(r.rev, r.tripRowId, 3, 3600, 3660, true, 120, 60) },
                s("ScheduleTime (nulls)") { r, _ -> ScheduleTime(r.rev, r.tripRowId, 3, null, null, false, null, null) },
                s("Block") { r, k -> Block(r.rev, "B-$k", "WD", 3600, 7200, 2, listOf("R1", "R2")) },
                s("Block (no routes)") { r, k -> Block(r.rev, "B-$k", "WD", 0, 86_400, 0, emptyList()) },
                s("BlockTrip") { r, _ -> BlockTrip(r.rev, r.blockPk, r.tripRowId, 2, 300, true) },
                s("BlockTrip (nulls)") { r, _ -> BlockTrip(r.rev, r.blockPk, r.tripRowId, 0, null, null) },
                s("TravelTimesForStopPath") { r, _ ->
                    TravelTimesForStopPath(r.rev, r.tripPatternId, 3, 120, 30, TravelTimeSource.SCHEDULE)
                },
                s("TravelTimesForStopPath (nulls)") { r, _ -> TravelTimesForStopPath(r.rev, r.tripPatternId, 3, null, null) },
            )
    }
}
