package eu.transittrack.db

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
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
import eu.transittrack.gtfs.model.Agency
import eu.transittrack.gtfs.model.Area
import eu.transittrack.gtfs.model.Attribution
import eu.transittrack.gtfs.model.BookingRule
import eu.transittrack.gtfs.model.Calendar
import eu.transittrack.gtfs.model.CalendarDate
import eu.transittrack.gtfs.model.FareAttribute
import eu.transittrack.gtfs.model.FareLegJoinRule
import eu.transittrack.gtfs.model.FareLegRule
import eu.transittrack.gtfs.model.FareMedia
import eu.transittrack.gtfs.model.FareProduct
import eu.transittrack.gtfs.model.FareRule
import eu.transittrack.gtfs.model.FareTransferRule
import eu.transittrack.gtfs.model.FeedInfo
import eu.transittrack.gtfs.model.Frequency
import eu.transittrack.gtfs.model.Level
import eu.transittrack.gtfs.model.Location
import eu.transittrack.gtfs.model.LocationGroup
import eu.transittrack.gtfs.model.LocationGroupStop
import eu.transittrack.gtfs.model.Network
import eu.transittrack.gtfs.model.Pathway
import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.model.RevisionScopedTable
import eu.transittrack.gtfs.model.RiderCategory
import eu.transittrack.gtfs.model.Route
import eu.transittrack.gtfs.model.RouteNetwork
import eu.transittrack.gtfs.model.Shape
import eu.transittrack.gtfs.model.ShapePoint
import eu.transittrack.gtfs.model.Stop
import eu.transittrack.gtfs.model.StopArea
import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.Timeframe
import eu.transittrack.gtfs.model.Transfer
import eu.transittrack.gtfs.model.Translation
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.store.GtfsTables
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Interim: while JPA entities and Exposed tables describe the same rows, each must read what the
 * other wrote, field by field. One fully populated sample per revision-scoped GTFS entity (every
 * nullable column non-null, SMALLINT enums at the edges of their range). Deleted with the JPA
 * entities.
 */
@SpringBootTest
class CrossReadGtfsRowsTest(
    @Autowired val em: EntityManager,
    @Autowired val txManager: PlatformTransactionManager,
    @Autowired val exposedDatabase: Database,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val patterns: TripPatternRepository,
) : PostgresPerMethodTest() {
    /** What the samples point at: the revision, and a trip pattern for `trips.trip_pattern_id` (an FK). */
    class Fixture(
        val rev: Long,
        val tripPatternId: Long,
    )

    private lateinit var fx: Fixture

    @BeforeEach
    fun seed() {
        val feedId =
            feeds
                .save(
                    GtfsFeed(
                        code = "cross-rows",
                        name = "Cross rows",
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
        val pattern = patterns.save(TripPattern(rev, "K", "R1", null, 0, "H", "SH1", 2, null, tripCount = 1))
        fx = Fixture(rev, pattern.id!!)
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

    private fun tableFor(row: RevisionScoped): RevisionScopedTable<*> = GtfsTables.ALL.single { it.rowClass == row.javaClass }

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
        private val D1: LocalDate = LocalDate.of(2026, 1, 2)
        private val D2: LocalDate = LocalDate.of(2026, 12, 31)

        private fun extent() = Extent.of(listOf(Point(45.70, 21.15), Point(45.80, 21.30)))

        private fun s(
            name: String,
            f: (Fixture, String) -> RevisionScoped,
        ): Arguments = Arguments.of(name, f)

        @JvmStatic
        fun samples(): Stream<Arguments> =
            Stream.of(
                s("Agency") { r, k ->
                    Agency(
                        r.rev, "A-$k", "Agency", "http://a", ZoneId.of("Europe/Bucharest"), Locale.of("pt", "BR"), "+40", "http://fare",
                        "a@x", extent(),
                    )
                },
                s("Stop") { r, k ->
                    Stop(
                        r.rev, "S-$k", "C1", "Stop", "Tts", "Desc", 45.75, 21.22, "Z1", "http://s", SMALL_MAX, "P1",
                        ZoneId.of("Europe/Bucharest"), SMALL_MIN, "L1", "4A", true, false, true, false,
                    )
                },
                s("Route") { r, k ->
                    Route(
                        r.rev, "R-$k", "A1", "33", "Long", "Desc", SMALL_MAX, "http://r", "FF0000", "FFFFFF", 7, SMALL_MIN, 2,
                        "N1", 12.5, true, extent(),
                    )
                },
                s("Trip") { r, k ->
                    Trip(
                        r.rev, "R1", "WD", "T-$k", "Head", "Short", SMALL_MAX, "B1", "SH1", SMALL_MIN, 2, r.tripPatternId, 3600, 7200, true,
                        false,
                    )
                },
                s("StopTime") { r, k ->
                    StopTime(
                        r.rev, "T-$k", 3, "S1", 3600, 3660, "LG1", "LOC1", "Head", 10, 20, SMALL_MAX, SMALL_MIN, 1, 2, 123.4, 1,
                        "BR1", "BR2",
                    )
                },
                s("Calendar") { r, k -> Calendar(r.rev, "SVC-$k", true, false, true, false, true, false, true, D1, D2) },
                s("CalendarDate") { r, k -> CalendarDate(r.rev, "SVC-$k", D1, SMALL_MAX) },
                s("FeedInfo") { r, _ -> FeedInfo(r.rev, "Pub", "http://p", "ro", "en", D1, D2, "v1", "c@x", "http://c") },
                s("Frequency") { r, k -> Frequency(r.rev, "T-$k", 3600, 7200, 600, SMALL_MIN) },
                s("FareAttribute") { r, k -> FareAttribute(r.rev, "F-$k", 2.5, "RON", SMALL_MAX, SMALL_MIN, "A1", 3600) },
                s("FareRule") { r, k -> FareRule(r.rev, "F-$k", "R1", "O1", "D1", "C1") },
                s("Timeframe") { r, k -> Timeframe(r.rev, "TF-$k", 0, 86399, "WD") },
                s("RiderCategory") { r, k -> RiderCategory(r.rev, "RC-$k", "Adult", SMALL_MAX, "http://e") },
                s("FareMedia") { r, k -> FareMedia(r.rev, "FM-$k", "Card", SMALL_MIN) },
                s("FareProduct") { r, k -> FareProduct(r.rev, "FP-$k", "Single", "RC1", "FM1", 3.0, "RON") },
                s("FareLegRule") { r, _ -> FareLegRule(r.rev, "LG1", "N1", "A1", "A2", "TF1", "TF2", "FP1", 5) },
                s("FareLegJoinRule") { r, _ -> FareLegJoinRule(r.rev, "N1", "N2", "S1", "S2") },
                s("FareTransferRule") { r, _ -> FareTransferRule(r.rev, "LG1", "LG2", 2, 3600, SMALL_MAX, SMALL_MIN, "FP1") },
                s("Area") { r, k -> Area(r.rev, "AR-$k", "Area") },
                s("StopArea") { r, k -> StopArea(r.rev, "AR-$k", "S1") },
                s("Network") { r, k -> Network(r.rev, "N-$k", "Net") },
                s("RouteNetwork") { r, k -> RouteNetwork(r.rev, "N1", "R-$k") },
                s("Pathway") { r, k ->
                    Pathway(r.rev, "PW-$k", "S1", "S2", SMALL_MAX, SMALL_MIN, 12.5, 30, 10, 0.08, 1.2, "Exit", "Entry")
                },
                s("Level") { r, k -> Level(r.rev, "L-$k", -1.5, "Basement") },
                s("LocationGroup") { r, k -> LocationGroup(r.rev, "LG-$k", "Group") },
                s("LocationGroupStop") { r, k -> LocationGroupStop(r.rev, "LG-$k", "S1") },
                // Postgres normal form for jsonb text (keys ordered by length, then bytes).
                s("Location") { r, k -> Location(r.rev, "LOC-$k", "Name", "Desc", """{"type": "Point", "coordinates": [21.2, 45.7]}""") },
                s("BookingRule") { r, k ->
                    BookingRule(
                        r.rev, "BR-$k", SMALL_MAX, 10, 60, 1, 64800, 7, 28800, "WD", "Msg", "Pick", "Drop", "+40", "http://i", "http://b",
                    )
                },
                s("Transfer") { r, _ -> Transfer(r.rev, "S1", "S2", "R1", "R2", "T1", "T2", SMALL_MIN, 120) },
                s("Translation") { r, _ -> Translation(r.rev, "stops", "stop_name", "en", "Station", "S1", "1", "Statie") },
                s("Attribution") { r, _ ->
                    Attribution(r.rev, "AT1", "A1", "R1", "T1", "Org", SMALL_MAX, SMALL_MIN, 1, "http://o", "o@x", "+40")
                },
                s("Shape") { r, k -> Shape(r.rev, "SH-$k", 12, 1234.5) },
                s("ShapePoint") { r, k -> ShapePoint(r.rev, "SH-$k", 45.75, 21.22, 4, 99.5) },
            )
    }
}
