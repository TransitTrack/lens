package eu.transittrack.db

import org.jetbrains.exposed.v1.core.Table

/**
 * Every Exposed table definition in the application. Liquibase owns the schema; this list is what
 * `SchemaDriftTest` checks against it, so each table must be added here when it is defined.
 */
object ExposedSchema {
    val ALL: List<Table> = emptyList()
}
