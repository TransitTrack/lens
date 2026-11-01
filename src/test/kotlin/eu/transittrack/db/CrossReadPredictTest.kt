package eu.transittrack.db

import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.predict.FrequencyRunState
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.AvlStopCrossingTable
import eu.transittrack.predict.model.KalmanTravelTimeState
import eu.transittrack.predict.model.KalmanTravelTimeStateTable
import eu.transittrack.predict.model.PredictionAccuracy
import eu.transittrack.predict.model.PredictionAccuracyTable
import eu.transittrack.predict.model.PredictionCheckpoint
import eu.transittrack.predict.model.PredictionCheckpointTable
import eu.transittrack.predict.model.PredictionFrequencyRunTable
import eu.transittrack.predict.model.TravelTimeObservation
import eu.transittrack.predict.model.TravelTimeObservationTable
import eu.transittrack.predict.model.VehiclePrediction
import eu.transittrack.predict.model.VehiclePredictionTable
import eu.transittrack.support.DstTimeZoneExtension
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Interim: while JPA entities and Exposed tables describe the same prediction rows, each must read
 * what the other wrote, field by field. Samples cover every [PredictionAlgorithm], [DayType] and
 * [TimeBand] value (all ordinal-mapped), a fully populated row and one with every nullable column
 * null, and an instant inside a DST fall-back hour (with the JVM zone pinned to one that has DST).
 * `prediction_frequency_run` has no entity: its other side is the native SQL the Hibernate-era
 * writer used. Deleted with the JPA entities.
 */
@SpringBootTest
@ExtendWith(DstTimeZoneExtension::class)
class CrossReadPredictTest(
    @Autowired val em: EntityManager,
    @Autowired val txManager: PlatformTransactionManager,
    @Autowired val exposedDatabase: Database,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val jdbc: JdbcTemplate,
) : PostgresPerMethodTest() {
    @BeforeEach
    fun seed() {
        feeds.save(
            AvlFeed(
                code = FEED, name = "Parent", gtfsFeedCode = "g", url = "http://x", format = AvlFormat.GTFS_RT,
                pollIntervalSec = 15, assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR, enabled = true, headers = null,
                source = AvlFeedSourceKind.CONFIG, createdAt = T0, updatedAt = T0,
            ),
        )
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
    @MethodSource("crossingSamples")
    fun crossingJpaWritesExposedReads(
        name: String,
        sample: (String) -> AvlStopCrossing,
    ) = jpaWritesExposedReads(AvlStopCrossingTable, AvlStopCrossingTable.id, sample("jpa"), AvlStopCrossing::id)

    @ParameterizedTest(name = "{0}")
    @MethodSource("crossingSamples")
    fun crossingExposedWritesJpaReads(
        name: String,
        sample: (String) -> AvlStopCrossing,
    ) = exposedWritesJpaReads(AvlStopCrossingTable, AvlStopCrossingTable.id, sample("exposed")) { r, id -> r.id = id }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kalmanSamples")
    fun kalmanJpaWritesExposedReads(
        name: String,
        sample: (String) -> KalmanTravelTimeState,
    ) = jpaWritesExposedReads(KalmanTravelTimeStateTable, KalmanTravelTimeStateTable.id, sample("jpa"), KalmanTravelTimeState::id)

    @ParameterizedTest(name = "{0}")
    @MethodSource("kalmanSamples")
    fun kalmanExposedWritesJpaReads(
        name: String,
        sample: (String) -> KalmanTravelTimeState,
    ) = exposedWritesJpaReads(KalmanTravelTimeStateTable, KalmanTravelTimeStateTable.id, sample("exposed")) { r, id -> r.id = id }

    @ParameterizedTest(name = "{0}")
    @MethodSource("observationSamples")
    fun observationJpaWritesExposedReads(
        name: String,
        sample: (String) -> TravelTimeObservation,
    ) = jpaWritesExposedReads(TravelTimeObservationTable, TravelTimeObservationTable.id, sample("jpa"), TravelTimeObservation::id)

    @ParameterizedTest(name = "{0}")
    @MethodSource("observationSamples")
    fun observationExposedWritesJpaReads(
        name: String,
        sample: (String) -> TravelTimeObservation,
    ) = exposedWritesJpaReads(TravelTimeObservationTable, TravelTimeObservationTable.id, sample("exposed")) { r, id -> r.id = id }

    @ParameterizedTest(name = "{0}")
    @MethodSource("accuracySamples")
    fun accuracyJpaWritesExposedReads(
        name: String,
        sample: (String) -> PredictionAccuracy,
    ) = jpaWritesExposedReads(PredictionAccuracyTable, PredictionAccuracyTable.id, sample("jpa"), PredictionAccuracy::id)

    @ParameterizedTest(name = "{0}")
    @MethodSource("accuracySamples")
    fun accuracyExposedWritesJpaReads(
        name: String,
        sample: (String) -> PredictionAccuracy,
    ) = exposedWritesJpaReads(PredictionAccuracyTable, PredictionAccuracyTable.id, sample("exposed")) { r, id -> r.id = id }

    @ParameterizedTest(name = "{0}")
    @MethodSource("checkpointSamples")
    fun checkpointJpaWritesExposedReads(
        name: String,
        sample: (String) -> PredictionCheckpoint,
    ) = jpaWritesExposedReads(PredictionCheckpointTable, PredictionCheckpointTable.id, sample("jpa"), PredictionCheckpoint::id)

    @ParameterizedTest(name = "{0}")
    @MethodSource("checkpointSamples")
    fun checkpointExposedWritesJpaReads(
        name: String,
        sample: (String) -> PredictionCheckpoint,
    ) = exposedWritesJpaReads(PredictionCheckpointTable, PredictionCheckpointTable.id, sample("exposed")) { r, id -> r.id = id }

    @ParameterizedTest(name = "{0}")
    @MethodSource("predictionSamples")
    fun predictionJpaWritesExposedReads(
        name: String,
        sample: (String) -> VehiclePrediction,
    ) = jpaWritesExposedReads(VehiclePredictionTable, VehiclePredictionTable.id, sample("jpa"), VehiclePrediction::id)

    @ParameterizedTest(name = "{0}")
    @MethodSource("predictionSamples")
    fun predictionExposedWritesJpaReads(
        name: String,
        sample: (String) -> VehiclePrediction,
    ) = exposedWritesJpaReads(VehiclePredictionTable, VehiclePredictionTable.id, sample("exposed")) { r, id -> r.id = id }

    /** Written with the Hibernate-era native upsert and its JDBC binding, read through [PredictionFrequencyRunTable]. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("frequencyRunSamples")
    fun frequencyRunNativeWritesExposedReads(
        name: String,
        written: FrequencyRunState,
    ) {
        jdbc.update(LEGACY_UPSERT_FREQUENCY_RUN_SQL) { ps ->
            ps.setString(1, FEED)
            ps.setString(2, "v-native")
            ps.setLong(3, 7L)
            ps.setObject(4, written.serviceDate)
            ps.setObject(5, OffsetDateTime.ofInstant(written.startedAt, ZoneOffset.UTC))
            ps.setObject(6, OffsetDateTime.ofInstant(written.lastReportTs, ZoneOffset.UTC))
            written.startTimeSec?.let { ps.setObject(7, it, Types.INTEGER) } ?: ps.setNull(7, Types.INTEGER)
            written.lastDistanceAlongTripM?.let { ps.setObject(8, it, Types.DOUBLE) } ?: ps.setNull(8, Types.DOUBLE)
        }
        val read =
            transaction(exposedDatabase) {
                PredictionFrequencyRunTable.read(
                    PredictionFrequencyRunTable.selectAll().where { PredictionFrequencyRunTable.vehicleId eq "v-native" }.single(),
                )
            }
        assertThat(read).isEqualTo(written)
    }

    /** Inserted through [PredictionFrequencyRunTable], read with the Hibernate-era native select and its JDBC getters. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("frequencyRunSamples")
    fun frequencyRunExposedWritesNativeReads(
        name: String,
        written: FrequencyRunState,
    ) {
        transaction(exposedDatabase) {
            PredictionFrequencyRunTable.insert {
                it[feedCode] = FEED
                it[vehicleId] = "v-exposed"
                it[tripRowId] = 7L
                it[serviceDate] = written.serviceDate
                it[runStartedAt] = written.startedAt
                it[lastReportTs] = written.lastReportTs
                it[startTimeSec] = written.startTimeSec
                it[lastDistanceAlongTripM] = written.lastDistanceAlongTripM
            }
        }
        val read =
            jdbc
                .query(LEGACY_SELECT_FREQUENCY_RUN_SQL, { rs, _ ->
                    FrequencyRunState(
                        rs.getObject("service_date", LocalDate::class.java),
                        rs.getObject("run_started_at", OffsetDateTime::class.java).toInstant(),
                        rs.getObject("last_report_ts", OffsetDateTime::class.java).toInstant(),
                        rs.getInt("start_time_sec").let { if (rs.wasNull()) null else it },
                        rs.getDouble("last_distance_along_trip_m").let { if (rs.wasNull()) null else it },
                    )
                }, FEED, "v-exposed", 7L)
                .single()
        assertThat(read).isEqualTo(written)
    }

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
        private const val FEED = "parent"
        private val T0 = Instant.parse("2026-09-01T00:00:00.123456Z")
        private val DST_OVERLAP = DstTimeZoneExtension.DST_FALL_BACK

        private const val LEGACY_UPSERT_FREQUENCY_RUN_SQL = """
            insert into prediction_frequency_run
                (feed_code, vehicle_id, trip_row_id, service_date, run_started_at, last_report_ts,
                 start_time_sec, last_distance_along_trip_m)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (feed_code, vehicle_id, trip_row_id) do update set
                service_date = excluded.service_date, run_started_at = excluded.run_started_at,
                last_report_ts = excluded.last_report_ts, start_time_sec = excluded.start_time_sec,
                last_distance_along_trip_m = excluded.last_distance_along_trip_m
            where prediction_frequency_run.last_report_ts <= excluded.last_report_ts
        """

        private const val LEGACY_SELECT_FREQUENCY_RUN_SQL =
            "select service_date, run_started_at, last_report_ts, start_time_sec, last_distance_along_trip_m " +
                "from prediction_frequency_run where feed_code = ? and vehicle_id = ? and trip_row_id = ?"

        private fun crossing(
            vehicle: String,
            nulls: Boolean,
        ) = AvlStopCrossing(
            feedCode = FEED,
            vehicleId = vehicle,
            tripRowId = 7,
            tripPatternId = 4,
            stopPathIndex = 2,
            observedAt = DST_OVERLAP,
            observedTravelTimeSec = if (nulls) null else 93.25,
            serviceId = if (nulls) null else "WK",
            tripStartSec = if (nulls) null else 90_000,
            routeId = if (nulls) null else "R1",
            directionId = if (nulls) null else 1,
            revisionId = 9,
            runStartedAt = if (nulls) null else T0,
            reportGapSec = if (nulls) null else 31,
        )

        @JvmStatic
        fun crossingSamples(): Stream<Arguments> =
            Stream.of(
                Arguments.of("AvlStopCrossing", { k: String -> crossing("v-$k-full", nulls = false) }),
                Arguments.of("AvlStopCrossing (nulls)", { k: String -> crossing("v-$k-null", nulls = true) }),
            )

        /** Every day type and every time band, each in at least one sample. */
        private fun buckets(): List<Pair<DayType, TimeBand>> =
            TimeBand.entries.mapIndexed { i, band -> DayType.entries[i % DayType.entries.size] to band }

        @JvmStatic
        fun kalmanSamples(): Stream<Arguments> =
            buckets()
                .map { (day, band) ->
                    Arguments.of(
                        "KalmanTravelTimeState $day/$band",
                        { k: String ->
                            KalmanTravelTimeState(
                                gtfsFeedCode = "g", patternKey = "p-$k-$day-$band", stopPathIndex = 3, dayType = day, timeBand = band,
                                estimateSec = 120.5, errorVariance = 36.125, sampleCount = 11, updatedAt = DST_OVERLAP,
                            )
                        },
                    )
                }.stream()

        @JvmStatic
        fun observationSamples(): Stream<Arguments> =
            buckets()
                .map { (day, band) ->
                    Arguments.of(
                        "TravelTimeObservation $day/$band",
                        { k: String ->
                            TravelTimeObservation(
                                gtfsFeedCode = "g", patternKey = "p-$k-$day-$band", stopPathIndex = 3, dayType = day, timeBand = band,
                                sampleCount = 5, meanSec = 88.75, updatedAt = DST_OVERLAP,
                            )
                        },
                    )
                }.stream()

        private fun accuracy(
            vehicle: String,
            algorithm: PredictionAlgorithm,
            bucket: Pair<DayType, TimeBand>?,
        ) = PredictionAccuracy(
            feedCode = FEED,
            vehicleId = vehicle,
            tripRowId = 7,
            stopPathIndex = 2,
            algorithm = algorithm,
            predictedTs = if (bucket == null) null else T0.plusSeconds(600),
            actualTs = DST_OVERLAP,
            errorSec = if (bucket == null) null else -45,
            absErrorSec = if (bucket == null) null else 45,
            createdAt = T0,
            horizonSec = if (bucket == null) null else 300,
            predictedAt = if (bucket == null) null else T0.plusSeconds(1),
            producedAt = if (bucket == null) null else T0.plusSeconds(2),
            actualLeadSec = if (bucket == null) null else 290,
            confidenceSec = if (bucket == null) null else 60,
            routeId = if (bucket == null) null else "R1",
            directionId = if (bucket == null) null else 0,
            dayType = bucket?.first,
            timeBand = bucket?.second,
        )

        @JvmStatic
        fun accuracySamples(): Stream<Arguments> =
            (
                buckets().mapIndexed { i, bucket ->
                    val algorithm = PredictionAlgorithm.entries[i % PredictionAlgorithm.entries.size]
                    Arguments.of(
                        "PredictionAccuracy $algorithm ${bucket.first}/${bucket.second}",
                        { k: String -> accuracy("v-$k-$i", algorithm, bucket) },
                    )
                } +
                    Arguments.of(
                        "PredictionAccuracy (nulls)",
                        { k: String -> accuracy("v-$k-null", PredictionAlgorithm.KALMAN, null) },
                    )
            ).stream()

        private fun checkpoint(
            vehicle: String,
            algorithm: PredictionAlgorithm,
            nulls: Boolean,
        ) = PredictionCheckpoint(
            feedCode = FEED,
            vehicleId = vehicle,
            tripRowId = 7,
            stopPathIndex = 2,
            algorithm = algorithm,
            predictedTs = if (nulls) null else T0.plusSeconds(600),
            computedAt = DST_OVERLAP,
            sampleSlot = T0,
            confidenceSec = if (nulls) null else 60,
            serviceDate = if (nulls) null else LocalDate.parse("2026-09-01"),
            producedAt = if (nulls) null else T0.plusSeconds(1),
        )

        @JvmStatic
        fun checkpointSamples(): Stream<Arguments> =
            (
                PredictionAlgorithm.entries.map { a ->
                    Arguments.of("PredictionCheckpoint $a", { k: String -> checkpoint("v-$k-$a", a, nulls = false) })
                } +
                    Arguments.of(
                        "PredictionCheckpoint (nulls)",
                        { k: String -> checkpoint("v-$k-null", PredictionAlgorithm.KALMAN, nulls = true) },
                    )
            ).stream()

        private fun prediction(
            vehicle: String,
            algorithm: PredictionAlgorithm,
            nulls: Boolean,
        ) = VehiclePrediction(
            feedCode = FEED,
            vehicleId = vehicle,
            tripRowId = 7,
            blockPk = if (nulls) null else 3,
            tripPatternId = 4,
            stopPathIndex = 2,
            algorithm = algorithm,
            predictedArrivalTs = if (nulls) null else DST_OVERLAP,
            predictedDepartureTs = if (nulls) null else DST_OVERLAP.plusSeconds(20),
            actualArrivalTs = if (nulls) null else T0.plusSeconds(610),
            actualDepartureTs = if (nulls) null else T0.plusSeconds(630),
            confidenceSec = if (nulls) null else 75,
            computedAt = T0,
            sourceReportTs = if (nulls) null else T0.minusSeconds(3),
            serviceDate = if (nulls) null else LocalDate.parse("2026-09-01"),
            actualReportGapSec = if (nulls) null else 29,
        )

        @JvmStatic
        fun predictionSamples(): Stream<Arguments> =
            (
                PredictionAlgorithm.entries.map { a ->
                    Arguments.of("VehiclePrediction $a", { k: String -> prediction("v-$k-$a", a, nulls = false) })
                } +
                    Arguments.of(
                        "VehiclePrediction (nulls)",
                        { k: String -> prediction("v-$k-null", PredictionAlgorithm.KALMAN, nulls = true) },
                    )
            ).stream()

        @JvmStatic
        fun frequencyRunSamples(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "FrequencyRun",
                    FrequencyRunState(LocalDate.parse("2026-10-25"), DST_OVERLAP, DST_OVERLAP.plusSeconds(45), 90_000, 1_234.5),
                ),
                Arguments.of("FrequencyRun (nulls)", FrequencyRunState(LocalDate.parse("2026-09-01"), T0, T0.plusSeconds(1), null, null)),
            )
    }
}
