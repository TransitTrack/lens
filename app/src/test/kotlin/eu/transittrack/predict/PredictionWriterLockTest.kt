package eu.transittrack.predict

import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.KalmanState
import eu.transittrack.predict.learn.LearningKey
import eu.transittrack.predict.learn.RunningAverageState
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.learn.TravelTimeBucket
import eu.transittrack.predict.model.KalmanTravelTimeStateTable
import eu.transittrack.predict.model.PredictionFrequencyRunTable
import eu.transittrack.predict.model.TravelTimeObservationTable
import eu.transittrack.support.PostgresPerMethodTest

/**
 * The locks [PredictionWriter] takes inside [PredictionWriter.inTransaction] are held until that
 * transaction commits: learner rows `FOR UPDATE` (probed with `FOR KEY SHARE`, which only `FOR UPDATE`
 * blocks, so a weaker `FOR NO KEY UPDATE` would fail these), and the transaction-scoped advisory locks.
 */
@PostgresSliceTest
@Import(PredictionWriter::class)
class PredictionWriterLockTest(
    @Autowired val writer: PredictionWriter,
    @Autowired val exposedDatabase: Database,
    @Autowired val feeds: AvlFeedRepository,
) : PostgresPerMethodTest() {
    private val key = LearningKey("g", "RA|SHP|S1_to_S4|abc")
    private val bucket = LearnerBucket(2, TravelTimeBucket(DayType.WEEKDAY, TimeBand.MORNING_PEAK))
    private val at = Instant.parse("2026-09-04T10:00:00Z")

    /**
     * Runs [holder] in thread A while it keeps its transaction open for [holdMs], then [contender]
     * in thread B; returns how long B took.
     */
    private fun contend(
        holdMs: Long,
        holder: () -> Unit,
        contender: () -> Unit,
    ): Long {
        val pool = Executors.newFixedThreadPool(2)
        val aHoldsLock = CountDownLatch(1)
        try {
            val a =
                pool.submit {
                    writer.inTransaction {
                        holder()
                        aHoldsLock.countDown()
                        Thread.sleep(holdMs)
                    }
                }
            check(aHoldsLock.await(10, TimeUnit.SECONDS)) { "thread A never took the lock" }
            val b = pool.submit<Long> { measureTimeMillis(contender) }
            val waited = b.get(10, TimeUnit.SECONDS)
            a.get(10, TimeUnit.SECONDS)
            return waited
        } finally {
            pool.shutdownNow()
        }
    }

    private fun keyShareObservationRow() =
        transaction(exposedDatabase) {
            TravelTimeObservationTable
                .selectAll()
                .where {
                    (TravelTimeObservationTable.gtfsFeedCode eq key.gtfsFeedCode) and
                        (TravelTimeObservationTable.patternKey eq key.patternKey)
                }.forUpdate(ForUpdateOption.PostgreSQL.ForKeyShare)
                .toList()
        }

    private fun keyShareKalmanRow() =
        transaction(exposedDatabase) {
            KalmanTravelTimeStateTable
                .selectAll()
                .where {
                    (KalmanTravelTimeStateTable.gtfsFeedCode eq key.gtfsFeedCode) and
                        (KalmanTravelTimeStateTable.patternKey eq key.patternKey)
                }.forUpdate(ForUpdateOption.PostgreSQL.ForKeyShare)
                .toList()
        }

    @Test
    fun observationLockBlocksSecondTransaction() {
        writer.inTransaction { writer.upsertObservations(key, mapOf(bucket to RunningAverageState(1L, 30.0)), at) }
        val waited = contend(500, { assertThat(writer.lockObservations(key, listOf(bucket)).size).isEqualTo(1) }) {
            assertThat(keyShareObservationRow().size).isEqualTo(1)
        }
        assertThat(waited).isGreaterThanOrEqualTo(450L)
    }

    @Test
    fun kalmanLockBlocksSecondTransaction() {
        writer.inTransaction { writer.upsertKalmanStates(key, mapOf(bucket to KalmanState(30.0, 5.0, 1L)), at) }
        val waited = contend(500, { assertThat(writer.lockKalmanStates(key, listOf(bucket)).size).isEqualTo(1) }) {
            assertThat(keyShareKalmanRow().size).isEqualTo(1)
        }
        assertThat(waited).isGreaterThanOrEqualTo(450L)
    }

    /** No learner rows exist, so only the advisory lock on (feed, pattern key) can make B wait. */
    @Test
    fun advisoryLockBlocksSecondTransaction() {
        val waited = contend(500, { writer.lockObservations(key, listOf(bucket)) }) {
            writer.inTransaction { writer.lockKalmanStates(key, listOf(bucket)) }
        }
        assertThat(waited).isGreaterThanOrEqualTo(450L)
    }

    /** The frequency-run read takes its own advisory lock, plus `FOR UPDATE` on the run row. */
    @Test
    fun frequencyRunLockBlocksSecondTransaction() {
        seedFeed()
        writer.inTransaction {
            writer.saveFrequencyRun("f1", "v1", 1L, FrequencyRunState(java.time.LocalDate.parse("2026-09-04"), at, at, null))
        }
        val waited = contend(500, { writer.frequencyRun("f1", "v1", 1L) }) {
            transaction(exposedDatabase) {
                PredictionFrequencyRunTable
                    .selectAll()
                    .where { PredictionFrequencyRunTable.vehicleId eq "v1" }
                    .forUpdate(ForUpdateOption.PostgreSQL.ForKeyShare)
                    .toList()
            }
        }
        assertThat(waited).isGreaterThanOrEqualTo(450L)
    }

    /** Another pattern key is another advisory lock, and other rows: B proceeds while A still holds its locks. */
    @Test
    fun lockOnOtherKeyDoesNotBlock() {
        val other = LearningKey("g", "other")
        val waited = contend(1500, { writer.lockObservations(key, listOf(bucket)) }) {
            writer.inTransaction { writer.lockObservations(other, listOf(bucket)) }
        }
        assertThat(waited).isLessThan(1000L)
    }

    private fun seedFeed() {
        feeds.save(
            AvlFeed(
                code = "f1", name = "F1", gtfsFeedCode = "g", url = "http://x",
                format = AvlFormat.GTFS_RT, pollIntervalSec = 30,
                assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR, enabled = true,
                headers = null, source = AvlFeedSourceKind.CONFIG,
                createdAt = Instant.now(), updatedAt = Instant.now(),
            ),
        )
    }
}
