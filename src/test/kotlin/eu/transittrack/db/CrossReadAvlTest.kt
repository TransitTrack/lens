package eu.transittrack.db

import java.time.Instant
import java.time.LocalDate
import java.util.stream.Stream
import jakarta.persistence.EntityManager
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlFeedTable
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.AvlReportTable
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.PredictionStatus
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleMatchTable
import eu.transittrack.avl.model.VehicleState
import eu.transittrack.avl.model.VehicleStateTable
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.support.DstTimeZoneExtension
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Interim: while JPA entities and Exposed tables describe the same AVL rows, each must read what the
 * other wrote, field by field. Samples cover every [MatchStatus], [PredictionStatus],
 * [AvlFeedSourceKind], [AvlFormat] and [AvlAssignmentMode] value (all ordinal- or name-mapped), a
 * fully populated row and one with every nullable column null, and an instant inside a DST fall-back
 * hour (with the JVM zone pinned to one that has DST). Deleted with the JPA entities.
 */
@SpringBootTest
@ExtendWith(DstTimeZoneExtension::class)
class CrossReadAvlTest(
    @Autowired val em: EntityManager,
    @Autowired val txManager: PlatformTransactionManager,
    @Autowired val exposedDatabase: Database,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
) : PostgresPerMethodTest() {
    private var reportId = 0L

    @BeforeEach
    fun seed() {
        feeds.save(feed("parent", AvlFeedSourceKind.CONFIG, AvlFormat.GTFS_RT, AvlAssignmentMode.TRUST_DESCRIPTOR, nulls = true))
        reportId = reports.save(report(MatchStatus.PENDING, "parent-v", nulls = true)).id!!
    }

    /** Persists [written] through JPA, reads it back through [table] and compares every field. */
    private fun <R : Any> jpaWritesExposedReads(
        table: RowTable<R>,
        id: Column<Long>,
        written: R,
        idOf: (R) -> Long?,
    ) {
        TransactionTemplate(txManager).executeWithoutResult {
            em.persist(written)
            em.flush()
        }
        val read = transaction(exposedDatabase) { table.read(table.selectAll().where { id eq idOf(written)!! }.single()) }
        assertSameFields(written, read)
    }

    /** Inserts [written] through [table], loads it back through JPA and compares every field. */
    private fun <R : Any> exposedWritesJpaReads(
        table: RowTable<R>,
        id: Column<Long>,
        written: R,
        setId: (R, Long) -> Unit,
    ) {
        val newId = transaction(exposedDatabase) { table.insert { table.bind(it, written) }[id] }
        setId(written, newId)
        val read = TransactionTemplate(txManager).execute { em.find(written.javaClass, newId) }
        assertThat(read, name = "JPA row").isNotNull()
        assertSameFields(written, read!!)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("feedSamples")
    fun feedJpaWritesExposedReads(
        name: String,
        sample: (String) -> AvlFeed,
    ) = jpaWritesExposedReads(AvlFeedTable, AvlFeedTable.id, sample("jpa"), AvlFeed::id)

    @ParameterizedTest(name = "{0}")
    @MethodSource("feedSamples")
    fun feedExposedWritesJpaReads(
        name: String,
        sample: (String) -> AvlFeed,
    ) = exposedWritesJpaReads(AvlFeedTable, AvlFeedTable.id, sample("exposed")) { r, id -> r.id = id }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reportSamples")
    fun reportJpaWritesExposedReads(
        name: String,
        sample: (String) -> AvlReportRow,
    ) = jpaWritesExposedReads(AvlReportTable, AvlReportTable.id, sample("jpa"), AvlReportRow::id)

    @ParameterizedTest(name = "{0}")
    @MethodSource("reportSamples")
    fun reportExposedWritesJpaReads(
        name: String,
        sample: (String) -> AvlReportRow,
    ) = exposedWritesJpaReads(AvlReportTable, AvlReportTable.id, sample("exposed")) { r, id -> r.id = id }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matchSamples")
    fun matchJpaWritesExposedReads(
        name: String,
        sample: (Long) -> VehicleMatch,
    ) = jpaWritesExposedReads(VehicleMatchTable, VehicleMatchTable.id, sample(reportId), VehicleMatch::id)

    @ParameterizedTest(name = "{0}")
    @MethodSource("matchSamples")
    fun matchExposedWritesJpaReads(
        name: String,
        sample: (Long) -> VehicleMatch,
    ) = exposedWritesJpaReads(VehicleMatchTable, VehicleMatchTable.id, sample(reportId)) { r, id -> r.id = id }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stateSamples")
    fun stateJpaWritesExposedReads(
        name: String,
        sample: (String) -> VehicleState,
    ) = jpaWritesExposedReads(VehicleStateTable, VehicleStateTable.id, sample("jpa"), VehicleState::id)

    @ParameterizedTest(name = "{0}")
    @MethodSource("stateSamples")
    fun stateExposedWritesJpaReads(
        name: String,
        sample: (String) -> VehicleState,
    ) = exposedWritesJpaReads(VehicleStateTable, VehicleStateTable.id, sample("exposed")) { r, id -> r.id = id }

    /** Every stored property. */
    private fun assertSameFields(
        expected: Any,
        actual: Any,
    ) {
        for (p in expected::class.memberProperties) {
            if (p.javaField == null) continue
            assertThat(p.getter.call(actual), name = p.name).isEqualTo(p.getter.call(expected))
        }
    }

    companion object {
        private val T0 = Instant.parse("2026-09-01T00:00:00.123456Z")

        private val DST_OVERLAP = DstTimeZoneExtension.DST_FALL_BACK

        private fun feed(
            code: String,
            source: AvlFeedSourceKind,
            format: AvlFormat,
            mode: AvlAssignmentMode,
            nulls: Boolean,
        ) = AvlFeed(
            code = code,
            name = "Feed $code",
            gtfsFeedCode = "g-$code",
            url = "http://x/$code",
            format = format,
            pollIntervalSec = 15,
            assignmentMode = mode,
            predictionAlgorithms = if (nulls) listOf(PredictionAlgorithm.SCHEDULE_ADHERENCE) else PredictionAlgorithm.entries,
            enabled = !nulls,
            headers = if (nulls) null else mapOf("Authorization" to "Bearer x", "X-Feed" to code),
            source = source,
            lastPollAt = if (nulls) null else DST_OVERLAP,
            lastPollStatus = if (nulls) null else "ERR: boom",
            lastPollReportCount = if (nulls) null else 42,
            createdAt = T0,
            updatedAt = T0.plusSeconds(60),
        )

        @JvmStatic
        fun feedSamples(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "AvlFeed CONFIG/GTFS_RT/TRUST_DESCRIPTOR",
                    { k: String ->
                        feed("$k-1", AvlFeedSourceKind.CONFIG, AvlFormat.GTFS_RT, AvlAssignmentMode.TRUST_DESCRIPTOR, nulls = false)
                    },
                ),
                Arguments.of(
                    "AvlFeed API/STPT/DESCRIPTOR_THEN_INFER",
                    { k: String ->
                        feed("$k-2", AvlFeedSourceKind.API, AvlFormat.STPT, AvlAssignmentMode.DESCRIPTOR_THEN_INFER, nulls = false)
                    },
                ),
                Arguments.of(
                    "AvlFeed (nulls) FULL_INFERENCE",
                    { k: String -> feed("$k-3", AvlFeedSourceKind.API, AvlFormat.GTFS_RT, AvlAssignmentMode.FULL_INFERENCE, nulls = true) },
                ),
            )

        private fun report(
            status: MatchStatus,
            vehicle: String,
            nulls: Boolean,
        ) = AvlReportRow(
            feedCode = "parent",
            vehicleId = vehicle,
            vehicleLabel = if (nulls) null else "L-$vehicle",
            ts = DST_OVERLAP,
            lat = 45.75,
            lon = 21.22,
            bearing = if (nulls) null else 359.5,
            speedMps = if (nulls) null else 12.25,
            odometerM = if (nulls) null else 123_456.5,
            descTripId = if (nulls) null else "T1",
            descRouteId = if (nulls) null else "R1",
            descDirectionId = if (nulls) null else 1,
            descStartDate = if (nulls) null else LocalDate.parse("2026-09-01"),
            descStartTimeSec = if (nulls) null else 90_000,
            descScheduleRelationship = if (nulls) null else 3,
            currentStopSequence = if (nulls) null else 17,
            currentStopId = if (nulls) null else "S17",
            currentStatus = if (nulls) null else 2,
            occupancyStatus = if (nulls) null else 8,
            congestionLevel = if (nulls) null else 4,
            matchStatus = status,
            matchedAt = if (nulls) null else T0.plusSeconds(5),
            createdAt = T0,
        )

        @JvmStatic
        fun reportSamples(): Stream<Arguments> =
            (
                MatchStatus.entries.map { s ->
                    Arguments.of("AvlReport $s", { k: String -> report(s, "v-$k-$s", nulls = false) })
                } +
                    Arguments.of("AvlReport (nulls)", { k: String -> report(MatchStatus.PENDING, "v-$k-null", nulls = true) })
            ).stream()

        private fun match(
            reportId: Long,
            status: PredictionStatus,
            nulls: Boolean,
        ) = VehicleMatch(
            avlReportId = reportId,
            feedCode = "parent",
            vehicleId = "parent-v",
            ts = DST_OVERLAP,
            revisionId = 9,
            tripRowId = 7,
            blockPk = if (nulls) null else 3,
            tripPatternId = if (nulls) null else 4,
            stopPathIndex = 2,
            distanceAlongTripM = 1_234.5,
            deviationM = 5.25,
            scheduleAdherenceSec = if (nulls) null else -30,
            snappedLat = 45.75,
            snappedLon = 21.22,
            heading = if (nulls) null else 90.5,
            score = if (nulls) null else 0.875,
            createdAt = T0,
            predictionStatus = status,
            predictedAt = if (nulls) null else T0.plusSeconds(9),
        )

        @JvmStatic
        fun matchSamples(): Stream<Arguments> =
            (
                PredictionStatus.entries.map { s ->
                    Arguments.of("VehicleMatch $s", { reportId: Long -> match(reportId, s, nulls = false) })
                } +
                    Arguments.of("VehicleMatch (nulls)", { reportId: Long -> match(reportId, PredictionStatus.PENDING, nulls = true) })
            ).stream()

        private fun state(
            vehicle: String,
            nulls: Boolean,
        ) = VehicleState(
            feedCode = "parent",
            vehicleId = vehicle,
            vehicleLabel = if (nulls) null else "L-$vehicle",
            reportTs = DST_OVERLAP,
            lat = 45.75,
            lon = 21.22,
            bearing = if (nulls) null else 180.25,
            speedMps = if (nulls) null else 8.5,
            occupancyStatus = if (nulls) null else 6,
            matched = !nulls,
            stale = nulls,
            consecutiveFailures = if (nulls) 0 else 3,
            revisionId = if (nulls) null else 9,
            tripRowId = if (nulls) null else 7,
            blockPk = if (nulls) null else 3,
            tripPatternId = if (nulls) null else 4,
            stopPathIndex = if (nulls) null else 2,
            distanceAlongTripM = if (nulls) null else 1_234.5,
            scheduleAdherenceSec = if (nulls) null else 45,
            snappedLat = if (nulls) null else 45.751,
            snappedLon = if (nulls) null else 21.221,
            updatedAt = T0,
        )

        @JvmStatic
        fun stateSamples(): Stream<Arguments> =
            Stream.of(
                Arguments.of("VehicleState", { k: String -> state("v-$k-full", nulls = false) }),
                Arguments.of("VehicleState (nulls)", { k: String -> state("v-$k-null", nulls = true) }),
            )
    }
}
