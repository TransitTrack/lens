package eu.transittrack.db

import org.jetbrains.exposed.v1.core.Table

import eu.transittrack.avl.model.AvlFeedTable
import eu.transittrack.avl.model.AvlReportTable
import eu.transittrack.avl.model.VehicleMatchTable
import eu.transittrack.avl.model.VehicleStateTable
import eu.transittrack.gtfs.draft.DraftEditTable
import eu.transittrack.gtfs.feed.GtfsFeedTable
import eu.transittrack.gtfs.revision.GtfsRevisionTable
import eu.transittrack.gtfs.store.GtfsTables
import eu.transittrack.predict.model.AvlStopCrossingTable
import eu.transittrack.predict.model.KalmanTravelTimeStateTable
import eu.transittrack.predict.model.PredictionAccuracyTable
import eu.transittrack.predict.model.PredictionCheckpointTable
import eu.transittrack.predict.model.PredictionFrequencyRunTable
import eu.transittrack.predict.model.TravelTimeObservationTable
import eu.transittrack.predict.model.VehiclePredictionTable
import eu.transittrack.schedule.model.ScheduleTables
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationTable
import eu.transittrack.schedule.optimize.model.OptimizationRunTable

/**
 * Every Exposed table definition in the application. Liquibase owns the schema; this list is what
 * `SchemaDriftTest` checks against it, so each table must be added here when it is defined.
 */
object ExposedSchema {
    val ALL: List<Table> = listOf(GtfsFeedTable, GtfsRevisionTable, DraftEditTable) +
        GtfsTables.ALL +
        ScheduleTables.ALL +
        listOf(OptimizationRunTable, OptimizationRecommendationTable) +
        listOf(AvlFeedTable, AvlReportTable, VehicleMatchTable, VehicleStateTable) +
        listOf(
            AvlStopCrossingTable,
            KalmanTravelTimeStateTable,
            PredictionAccuracyTable,
            PredictionCheckpointTable,
            PredictionFrequencyRunTable,
            TravelTimeObservationTable,
            VehiclePredictionTable,
        )

    /**
     * Exact statements that are known, accepted differences between the Exposed model and the
     * Liquibase schema. Every entry needs a comment saying why it is safe to ignore.
     */
    val IGNORED_DRIFT: Set<String> =
        setOf(
            // Liquibase (0012) dropped these ids' identity and set `default nextval('<table>_seq')`, which
            // Exposed reports as drift because it cannot model that default. Inserts name the sequence
            // explicitly (`autoIncrement(Sequence)`), so the DB-side default is never relied on from Exposed.
            "ALTER TABLE gtfs_feed ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE gtfs_revision ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE schedule_optimization_run ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE schedule_optimization_recommendation ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE avl_feed ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE avl_report ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE vehicle_match ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE vehicle_state ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE avl_stop_crossing ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE kalman_travel_time_state ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE prediction_accuracy ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE prediction_checkpoint ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE travel_time_observation ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
            "ALTER TABLE vehicle_prediction ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT",
        )
}
