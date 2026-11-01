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
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationStatus
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationTable
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.schedule.optimize.model.OptimizationRunState
import eu.transittrack.schedule.optimize.model.OptimizationRunTable
import eu.transittrack.support.DstTimeZoneExtension
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Interim: while JPA entities and Exposed tables describe the same optimization rows, each must read
 * what the other wrote, field by field (fully populated, all-nullable-null and DST fall-back-hour
 * samples). Deleted with the JPA entities.
 */
@SpringBootTest
@ExtendWith(DstTimeZoneExtension::class)
class CrossReadOptimizationTest(
    @Autowired val em: EntityManager,
    @Autowired val txManager: PlatformTransactionManager,
    @Autowired val exposedDatabase: Database,
    @Autowired val runs: OptimizationRunRepository,
) : PostgresPerMethodTest() {
    private var parentRunId = 0L

    @BeforeEach
    fun seed() {
        parentRunId = runs.save(run("parent")).id!!
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runSamples")
    fun runJpaWritesExposedReads(
        name: String,
        sample: (String) -> OptimizationRunRow,
    ) {
        val written = sample("jpa")
        TransactionTemplate(txManager).executeWithoutResult {
            em.persist(written)
            em.flush()
        }
        val read =
            transaction(exposedDatabase) {
                OptimizationRunTable.read(
                    OptimizationRunTable
                        .selectAll()
                        .where {
                            OptimizationRunTable.id eq
                                written.id!!
                        }.single(),
                )
            }
        assertSameFields(written, read)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runSamples")
    fun runExposedWritesJpaReads(
        name: String,
        sample: (String) -> OptimizationRunRow,
    ) {
        val written = sample("exposed")
        transaction(exposedDatabase) {
            written.id =
                OptimizationRunTable.insert { OptimizationRunTable.bind(it, written) }[OptimizationRunTable.id]
        }
        val read = TransactionTemplate(txManager).execute { em.find(OptimizationRunRow::class.java, written.id!!) }
        assertThat(read, name = "JPA row").isNotNull()
        assertSameFields(written, read!!)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recommendationSamples")
    fun recommendationJpaWritesExposedReads(
        name: String,
        sample: (Long) -> OptimizationRecommendationRow,
    ) {
        val written = sample(parentRunId)
        TransactionTemplate(txManager).executeWithoutResult {
            em.persist(written)
            em.flush()
        }
        val read =
            transaction(exposedDatabase) {
                OptimizationRecommendationTable.read(
                    OptimizationRecommendationTable.selectAll().where { OptimizationRecommendationTable.id eq written.id!! }.single(),
                )
            }
        assertSameFields(written, read, jsonb = true)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recommendationSamples")
    fun recommendationExposedWritesJpaReads(
        name: String,
        sample: (Long) -> OptimizationRecommendationRow,
    ) {
        val written = sample(parentRunId)
        transaction(exposedDatabase) {
            written.id =
                OptimizationRecommendationTable.insert {
                    OptimizationRecommendationTable.bind(it, written)
                }[OptimizationRecommendationTable.id]
        }
        val read = TransactionTemplate(txManager).execute { em.find(OptimizationRecommendationRow::class.java, written.id!!) }
        assertThat(read, name = "JPA row").isNotNull()
        assertSameFields(written, read!!, jsonb = true)
    }

    /** Every stored property; jsonb columns compare modulo Postgres' canonical text form (whitespace). */
    private fun assertSameFields(
        expected: Any,
        actual: Any,
        jsonb: Boolean = false,
    ) {
        for (p in expected::class.memberProperties) {
            if (p.javaField == null) continue
            val e = p.getter.call(expected)
            val a = p.getter.call(actual)
            if (jsonb && p.name in JSON_FIELDS) {
                assertThat(a?.let(::canonical), name = p.name).isEqualTo(e?.let(::canonical))
            } else {
                assertThat(a, name = p.name).isEqualTo(e)
            }
        }
    }

    private fun canonical(v: Any): String = v.toString().replace(" ", "")

    companion object {
        private val JSON_FIELDS = setOf("currentValue", "proposedValue", "evidence")
        private val T0 = Instant.parse("2026-09-01T00:00:00Z")

        private fun run(feed: String) =
            OptimizationRunRow(feedCode = feed, revisionId = 1, observedFrom = T0, observedTo = T0.plusSeconds(3600), minimumSamples = 1)

        @JvmStatic
        fun runSamples(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "OptimizationRun",
                    { k: String ->
                        OptimizationRunRow(
                            feedCode = "f-$k", revisionId = 42, serviceId = "WD", routeId = "R1", directionId = 1,
                            windowFromSec = 21_600, windowToSec = 36_000, observedFrom = T0, observedTo = T0.plusSeconds(604_800),
                            minimumSamples = 20, state = OptimizationRunState.FAILED, startedAt = T0.plusSeconds(5),
                            completedAt = T0.plusSeconds(9), error = "boom", createdAt = T0.plusSeconds(1),
                        )
                    },
                ),
                Arguments.of("OptimizationRun (nulls)", { k: String -> run("f-$k") }),
                Arguments.of(
                    "OptimizationRun (DST fall-back hour)",
                    { k: String ->
                        val t = DstTimeZoneExtension.DST_FALL_BACK
                        OptimizationRunRow(
                            feedCode = "f-$k", revisionId = 42, observedFrom = t, observedTo = t.plusSeconds(1),
                            minimumSamples = 1, state = OptimizationRunState.SUCCEEDED, startedAt = t.plusSeconds(2),
                            completedAt = t.plusSeconds(3), createdAt = t.plusSeconds(4),
                        )
                    },
                ),
            )

        @JvmStatic
        fun recommendationSamples(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "OptimizationRecommendation",
                    { runId: Long ->
                        OptimizationRecommendationRow(
                            runId = runId, kind = OptimizationRecommendationKind.TRIP_SHIFT, sampleCount = 20, deltaSec = -65,
                            reason = "why", status = OptimizationRecommendationStatus.UNAVAILABLE,
                            currentValue = """{"arrivalSec": 100}""", proposedValue = """{"arrivalSec": 165}""",
                            evidence = """{"sampleCount": 20}""", conflictKey = "trip:T1/stop:3",
                        )
                    },
                ),
                Arguments.of(
                    "OptimizationRecommendation (nulls)",
                    { runId: Long ->
                        OptimizationRecommendationRow(
                            runId = runId, kind = OptimizationRecommendationKind.STOP_TIME, sampleCount = 0, deltaSec = 0, reason = "r",
                        )
                    },
                ),
            )
    }
}
