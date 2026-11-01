package eu.transittrack.db

import org.jetbrains.exposed.v1.core.Table

import eu.transittrack.gtfs.draft.DraftEditTable
import eu.transittrack.gtfs.feed.GtfsFeedTable
import eu.transittrack.gtfs.revision.GtfsRevisionTable
import eu.transittrack.gtfs.store.GtfsTables
import eu.transittrack.schedule.model.ScheduleTables

/**
 * Every Exposed table definition in the application. Liquibase owns the schema; this list is what
 * `SchemaDriftTest` checks against it, so each table must be added here when it is defined.
 */
object ExposedSchema {
    val ALL: List<Table> = listOf(GtfsFeedTable, GtfsRevisionTable, DraftEditTable) + GtfsTables.ALL + ScheduleTables.ALL
}
