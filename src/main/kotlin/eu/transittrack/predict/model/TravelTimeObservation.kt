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
import org.jetbrains.exposed.v1.core.Column as ExposedColumn

/** Windowed mean stop-to-stop time for one stop path of a pattern, in one day-type/time-band bucket. */
@Entity
@Table(name = "travel_time_observation")
class TravelTimeObservation(
    @Column(name = "gtfs_feed_code", nullable = false) var gtfsFeedCode: String,
    @Column(name = "pattern_key", nullable = false) var patternKey: String,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Enumerated @Column(name = "day_type", nullable = false) var dayType: DayType,
    @Enumerated @Column(name = "time_band", nullable = false) var timeBand: TimeBand,
    @Column(name = "sample_count", nullable = false) var sampleCount: Long,
    @Column(name = "mean_sec", nullable = false) var meanSec: Double,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "travelTimeObservationSeq")
    @SequenceGenerator(name = "travelTimeObservationSeq", sequenceName = "travel_time_observation_seq", allocationSize = 50)
    var id: Long? = null,
)

/**
 * The key both learner tables share: one row per GTFS feed, pattern, stop path, day type and time
 * band (`uq_travel_time_obs_bucket` / `uq_kalman_state_bucket`).
 */
interface LearnerBucketColumns {
    val gtfsFeedCode: ExposedColumn<String>
    val patternKey: ExposedColumn<String>
    val stopPathIndex: ExposedColumn<Int>
    val dayType: ExposedColumn<DayType>
    val timeBand: ExposedColumn<TimeBand>
}

object TravelTimeObservationTable : RowTable<TravelTimeObservation>("travel_time_observation"), LearnerBucketColumns {
    val id = long("id").autoIncrement(Sequence("travel_time_observation_seq", startWith = 1, incrementBy = 50))
    override val gtfsFeedCode = varchar("gtfs_feed_code", 64)
    override val patternKey = varchar("pattern_key", 255)
    override val stopPathIndex = integer("stop_path_index")
    override val dayType = shortOrdinal<DayType>("day_type")
    override val timeBand = shortOrdinal<TimeBand>("time_band")
    val sampleCount = long("sample_count").default(0L)
    val meanSec = double("mean_sec")
    val updatedAt = timestampTz("updated_at")

    override val primaryKey = PrimaryKey(id, name = "travel_time_observation_pkey")

    init {
        uniqueIndex("uq_travel_time_obs_bucket", gtfsFeedCode, patternKey, stopPathIndex, dayType, timeBand)
    }

    override fun read(row: ResultRow) =
        TravelTimeObservation(
            gtfsFeedCode = row[gtfsFeedCode],
            patternKey = row[patternKey],
            stopPathIndex = row[stopPathIndex],
            dayType = row[dayType],
            timeBand = row[timeBand],
            sampleCount = row[sampleCount],
            meanSec = row[meanSec],
            updatedAt = row[updatedAt],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: TravelTimeObservation,
    ) {
        s[gtfsFeedCode] = row.gtfsFeedCode
        s[patternKey] = row.patternKey
        s[stopPathIndex] = row.stopPathIndex
        s[dayType] = row.dayType
        s[timeBand] = row.timeBand
        s[sampleCount] = row.sampleCount
        s[meanSec] = row.meanSec
        s[updatedAt] = row.updatedAt
    }
}

@Repository
@ExposedBridge
class TravelTimeObservationRepository {
    fun findInBucket(
        gtfsFeedCode: String,
        patternKey: String,
        stopPathIndex: Int,
        dayType: DayType,
        timeBand: TimeBand,
    ): TravelTimeObservation? =
        TravelTimeObservationTable
            .selectAll()
            .where {
                (TravelTimeObservationTable.gtfsFeedCode eq gtfsFeedCode) and (TravelTimeObservationTable.patternKey eq patternKey) and
                    (TravelTimeObservationTable.stopPathIndex eq stopPathIndex) and (TravelTimeObservationTable.dayType eq dayType) and
                    (TravelTimeObservationTable.timeBand eq timeBand)
            }.singleOrNull()
            ?.let(TravelTimeObservationTable::read)

    fun findByPattern(
        gtfsFeedCode: String,
        patternKey: String,
    ): List<TravelTimeObservation> =
        TravelTimeObservationTable
            .selectAll()
            .where { (TravelTimeObservationTable.gtfsFeedCode eq gtfsFeedCode) and (TravelTimeObservationTable.patternKey eq patternKey) }
            .map(TravelTimeObservationTable::read)

    fun findAll(): List<TravelTimeObservation> = TravelTimeObservationTable.selectAll().map(TravelTimeObservationTable::read)

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: TravelTimeObservation): TravelTimeObservation {
        val id = row.id
        if (id == null) {
            row.id = TravelTimeObservationTable.insert { TravelTimeObservationTable.bind(it, row) }[TravelTimeObservationTable.id]
        } else {
            TravelTimeObservationTable.update({ TravelTimeObservationTable.id eq id }) { TravelTimeObservationTable.bind(it, row) }
        }
        return row
    }

    fun saveAll(rows: Iterable<TravelTimeObservation>): List<TravelTimeObservation> = rows.map(::save)

    fun deleteAll() {
        TravelTimeObservationTable.deleteAll()
    }
}
