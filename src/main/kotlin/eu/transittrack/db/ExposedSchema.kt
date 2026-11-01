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
}
