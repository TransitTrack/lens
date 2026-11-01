package eu.transittrack.predict.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.date

import eu.transittrack.avl.model.AvlFeedTable
import eu.transittrack.db.RowTable
import eu.transittrack.predict.FrequencyRunState

/**
 * The run a vehicle is on of a frequency-based trip row, one row per (feed, vehicle, trip row). It
 * has no entity: [read] and [bind] map the run's state, and the key columns are set by the caller.
 */
object PredictionFrequencyRunTable : RowTable<FrequencyRunState>("prediction_frequency_run") {
    val feedCode =
        varchar("feed_code", 255).references(
            AvlFeedTable.code,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_prediction_frequency_run_feed",
        )
    val vehicleId = varchar("vehicle_id", 255)
    val tripRowId = long("trip_row_id")
    val serviceDate = date("service_date")
    val runStartedAt = timestampTz("run_started_at")
    val lastReportTs = timestampTz("last_report_ts")
    val startTimeSec = integer("start_time_sec").nullable()
    val lastDistanceAlongTripM = double("last_distance_along_trip_m").nullable()

    override val primaryKey = PrimaryKey(feedCode, vehicleId, tripRowId, name = "prediction_frequency_run_pkey")

    init {
        index("ix_prediction_frequency_run_report", false, lastReportTs)
    }

    override fun read(row: ResultRow) =
        FrequencyRunState(
            serviceDate = row[serviceDate],
            startedAt = row[runStartedAt],
            lastReportTs = row[lastReportTs],
            startTimeSec = row[startTimeSec],
            lastDistanceAlongTripM = row[lastDistanceAlongTripM],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: FrequencyRunState,
    ) {
        s[serviceDate] = row.serviceDate
        s[runStartedAt] = row.startedAt
        s[lastReportTs] = row.lastReportTs
        s[startTimeSec] = row.startTimeSec
        s[lastDistanceAlongTripM] = row.lastDistanceAlongTripM
    }
}
