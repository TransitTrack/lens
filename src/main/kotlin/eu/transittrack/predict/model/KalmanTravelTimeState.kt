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

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

import eu.transittrack.db.RowTable
import eu.transittrack.db.bridge.ExposedBridge
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand

/** Kalman stop-to-stop estimate for one stop path of a pattern, in one day-type/time-band bucket. */
@Entity
@Table(name = "kalman_travel_time_state")
class KalmanTravelTimeState(
    @Column(name = "gtfs_feed_code", nullable = false) var gtfsFeedCode: String,
    @Column(name = "pattern_key", nullable = false) var patternKey: String,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Enumerated @Column(name = "day_type", nullable = false) var dayType: DayType,
    @Enumerated @Column(name = "time_band", nullable = false) var timeBand: TimeBand,
    @Column(name = "estimate_sec", nullable = false) var estimateSec: Double,
    @Column(name = "error_variance", nullable = false) var errorVariance: Double,
    @Column(name = "sample_count", nullable = false) var sampleCount: Long,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "kalmanTravelTimeStateSeq")
    @SequenceGenerator(name = "kalmanTravelTimeStateSeq", sequenceName = "kalman_travel_time_state_seq", allocationSize = 50)
    var id: Long? = null,
)

object KalmanTravelTimeStateTable : RowTable<KalmanTravelTimeState>("kalman_travel_time_state"), LearnerBucketColumns {
    val id = long("id").autoIncrement(Sequence("kalman_travel_time_state_seq", startWith = 1, incrementBy = 50))
    override val gtfsFeedCode = varchar("gtfs_feed_code", 64)
    override val patternKey = varchar("pattern_key", 255)
    override val stopPathIndex = integer("stop_path_index")
    override val dayType = shortOrdinal<DayType>("day_type")
    override val timeBand = shortOrdinal<TimeBand>("time_band")
    val estimateSec = double("estimate_sec")
    val errorVariance = double("error_variance")
    val sampleCount = long("sample_count").default(0L)
    val updatedAt = timestampTz("updated_at")

    override val primaryKey = PrimaryKey(id, name = "kalman_travel_time_state_pkey")

    init {
        uniqueIndex("uq_kalman_state_bucket", gtfsFeedCode, patternKey, stopPathIndex, dayType, timeBand)
    }

    override fun read(row: ResultRow) =
        KalmanTravelTimeState(
            gtfsFeedCode = row[gtfsFeedCode],
            patternKey = row[patternKey],
            stopPathIndex = row[stopPathIndex],
            dayType = row[dayType],
            timeBand = row[timeBand],
            estimateSec = row[estimateSec],
            errorVariance = row[errorVariance],
            sampleCount = row[sampleCount],
            updatedAt = row[updatedAt],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: KalmanTravelTimeState,
    ) {
        s[gtfsFeedCode] = row.gtfsFeedCode
        s[patternKey] = row.patternKey
        s[stopPathIndex] = row.stopPathIndex
        s[dayType] = row.dayType
        s[timeBand] = row.timeBand
        s[estimateSec] = row.estimateSec
        s[errorVariance] = row.errorVariance
        s[sampleCount] = row.sampleCount
        s[updatedAt] = row.updatedAt
    }
}

@Repository
@ExposedBridge
class KalmanTravelTimeStateRepository {
    fun findInBucket(
        gtfsFeedCode: String,
        patternKey: String,
        stopPathIndex: Int,
        dayType: DayType,
        timeBand: TimeBand,
    ): KalmanTravelTimeState? =
        KalmanTravelTimeStateTable
            .selectAll()
            .where {
                (KalmanTravelTimeStateTable.gtfsFeedCode eq gtfsFeedCode) and (KalmanTravelTimeStateTable.patternKey eq patternKey) and
                    (KalmanTravelTimeStateTable.stopPathIndex eq stopPathIndex) and (KalmanTravelTimeStateTable.dayType eq dayType) and
                    (KalmanTravelTimeStateTable.timeBand eq timeBand)
            }.singleOrNull()
            ?.let(KalmanTravelTimeStateTable::read)

    fun findByPattern(
        gtfsFeedCode: String,
        patternKey: String,
    ): List<KalmanTravelTimeState> =
        KalmanTravelTimeStateTable
            .selectAll()
            .where { (KalmanTravelTimeStateTable.gtfsFeedCode eq gtfsFeedCode) and (KalmanTravelTimeStateTable.patternKey eq patternKey) }
            .map(KalmanTravelTimeStateTable::read)

    fun findAll(): List<KalmanTravelTimeState> = KalmanTravelTimeStateTable.selectAll().map(KalmanTravelTimeStateTable::read)

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: KalmanTravelTimeState): KalmanTravelTimeState {
        val id = row.id
        if (id == null) {
            row.id = KalmanTravelTimeStateTable.insert { KalmanTravelTimeStateTable.bind(it, row) }[KalmanTravelTimeStateTable.id]
        } else {
            KalmanTravelTimeStateTable.update({ KalmanTravelTimeStateTable.id eq id }) { KalmanTravelTimeStateTable.bind(it, row) }
        }
        return row
    }

    fun saveAll(rows: Iterable<KalmanTravelTimeState>): List<KalmanTravelTimeState> = rows.map(::save)

    fun deleteAll() {
        KalmanTravelTimeStateTable.deleteAll()
    }
}
