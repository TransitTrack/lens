package eu.transittrack.predict.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand

@Entity
@Table(name = "prediction_accuracy")
class PredictionAccuracy(
    @Column(name = "feed_code", nullable = false) var feedCode: String,
    @Column(name = "vehicle_id", nullable = false) var vehicleId: String,
    @Column(name = "trip_row_id", nullable = false) var tripRowId: Long,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Enumerated @Column(nullable = false) var algorithm: PredictionAlgorithm,
    @Column(name = "predicted_ts") var predictedTs: Instant?,
    @Column(name = "actual_ts", nullable = false) var actualTs: Instant,
    @Column(name = "error_sec") var errorSec: Int?,
    @Column(name = "abs_error_sec") var absErrorSec: Int?,
    @Column(name = "created_at", nullable = false) var createdAt: Instant,
    /** Actual lead-time bin's upper bound; null on early legacy rows. */
    @Column(name = "horizon_sec") var horizonSec: Int? = null,
    /** Source AVL report time; actual issuance is stored separately in [producedAt]. */
    @Column(name = "predicted_at") var predictedAt: Instant? = null,
    @Column(name = "produced_at") var producedAt: Instant? = null,
    @Column(name = "actual_lead_sec") var actualLeadSec: Int? = null,
    @Column(name = "confidence_sec") var confidenceSec: Int? = null,
    @Column(name = "route_id") var routeId: String? = null,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "direction_id") var directionId: Int? = null,
    @Enumerated @Column(name = "day_type") var dayType: DayType? = null,
    @Enumerated @Column(name = "time_band") var timeBand: TimeBand? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "predictionAccuracySeq")
    @SequenceGenerator(name = "predictionAccuracySeq", sequenceName = "prediction_accuracy_seq", allocationSize = 50)
    var id: Long? = null,
)

/** Accuracy of one algorithm at one lead-time horizon, aggregated over a time window. */
interface PredictionAccuracyAggregate {
    val algorithmOrdinal: Int
    val horizonSec: Int?
    val eligibleCount: Long
    val sampleCount: Long
    val pairedSampleCount: Long
    val meanErrorSec: Double?
    val meanAbsErrorSec: Double?
    val p90AbsErrorSec: Double?
    val p95AbsErrorSec: Double?
    val confidenceCoverage: Double?
    val confidenceSampleCount: Long
}

@Repository
interface PredictionAccuracyRepository : JpaRepository<PredictionAccuracy, Long> {
    /** Legacy ETA-selected checkpoints are excluded; only shared, actual-lead snapshots are comparable. */
    @Query(
        value = """
            with snapshots as (
                select * from prediction_accuracy
                where feed_code = :feedCode and algorithm in (:algorithms) and actual_ts > :since
                  and horizon_sec is not null and actual_lead_sec is not null and predicted_at is not null and produced_at is not null
                  and (cast(:routeId as text) is null or route_id = cast(:routeId as text))
                  and (cast(:directionId as smallint) is null or direction_id = cast(:directionId as smallint))
                  and (cast(:dayType as smallint) is null or day_type = cast(:dayType as smallint))
                  and (cast(:timeBand as smallint) is null or time_band = cast(:timeBand as smallint))
            ), cohorts as (
                select vehicle_id, trip_row_id, stop_path_index, horizon_sec, predicted_at, produced_at, actual_ts,
                       count(distinct algorithm) as eligible_algorithms,
                       count(distinct algorithm) filter (where error_sec is not null) as available_algorithms
                from snapshots
                group by vehicle_id, trip_row_id, stop_path_index, horizon_sec, predicted_at, produced_at, actual_ts
            )
            select s.algorithm as "algorithmOrdinal", s.horizon_sec as "horizonSec",
                   count(*) as "eligibleCount", count(s.error_sec) as "sampleCount",
                   count(*) filter (where c.available_algorithms = :algorithmCount) as "pairedSampleCount",
                   avg(s.error_sec) filter (where c.available_algorithms = :algorithmCount) as "meanErrorSec",
                   avg(s.abs_error_sec) filter (where c.available_algorithms = :algorithmCount) as "meanAbsErrorSec",
                   percentile_cont(0.90) within group (order by s.abs_error_sec)
                       filter (where c.available_algorithms = :algorithmCount) as "p90AbsErrorSec",
                   percentile_cont(0.95) within group (order by s.abs_error_sec)
                       filter (where c.available_algorithms = :algorithmCount) as "p95AbsErrorSec",
                   avg(case when s.confidence_sec is null or s.error_sec is null then null
                            when s.abs_error_sec <= s.confidence_sec then 1.0 else 0.0 end) as "confidenceCoverage",
                   count(*) filter (where s.confidence_sec is not null and s.error_sec is not null) as "confidenceSampleCount"
            from snapshots s join cohorts c using
                (vehicle_id, trip_row_id, stop_path_index, horizon_sec, predicted_at, produced_at, actual_ts)
            where c.eligible_algorithms = :algorithmCount
            group by grouping sets ((s.algorithm), (s.algorithm, s.horizon_sec))
            order by s.algorithm, s.horizon_sec nulls first
        """,
        nativeQuery = true,
    )
    fun aggregateAccuracy(
        @Param("feedCode") feedCode: String,
        @Param("algorithms") algorithms: Collection<Short>,
        @Param("algorithmCount") algorithmCount: Int,
        @Param("since") since: Instant,
        @Param("routeId") routeId: String?,
        @Param("directionId") directionId: Int?,
        @Param("dayType") dayType: Short?,
        @Param("timeBand") timeBand: Short?,
    ): List<PredictionAccuracyAggregate>

    @Query(
        "select e from PredictionAccuracy e where e.feedCode = :feedCode and e.algorithm = :algorithm " +
            "and e.createdAt > :since",
    )
    fun findRecentByFeedAndAlgorithm(
        @Param("feedCode") feedCode: String,
        @Param("algorithm") algorithm: PredictionAlgorithm,
        @Param("since") since: Instant,
    ): List<PredictionAccuracy>

    /**
     * Bounded evidence lookup for the optimization analysis pipeline
     * ([eu.transittrack.schedule.optimize.OptimizationAnalysisPipeline]): scoped to one feed, one
     * stop-path index, a set of trip rows, and the run's observed date range — never all feeds or
     * algorithms.
     */
    @Query(
        "select p from PredictionAccuracy p where p.feedCode in :feedCodes and p.tripRowId in :tripRowIds " +
            "and p.stopPathIndex = :stopPathIndex and p.createdAt >= :from and p.createdAt < :to " +
            "and p.errorSec is not null",
    )
    fun findForTargets(
        feedCodes: Collection<String>,
        tripRowIds: Collection<Long>,
        stopPathIndex: Int,
        from: Instant,
        to: Instant,
    ): List<PredictionAccuracy>
}
