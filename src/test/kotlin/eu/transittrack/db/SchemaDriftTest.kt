package eu.transittrack.db

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

import eu.transittrack.support.PostgresPerClassTest

/**
 * Liquibase owns the schema; this fails when an Exposed table definition in [ExposedSchema.ALL]
 * disagrees with it, listing the DDL Exposed would run to make the database match its model.
 */
@SpringBootTest
class SchemaDriftTest(
    @Autowired val db: Database,
) : PostgresPerClassTest() {
    @Test
    fun exposedTablesMatchLiquibaseSchema() {
        val drift =
            transaction(db) {
                MigrationUtils.statementsRequiredForDatabaseMigration(*ExposedSchema.ALL.toTypedArray(), withLogs = false)
            }

        assertThat(drift.filterNot { it in ExposedSchema.IGNORED_DRIFT }, name = "DDL Exposed would run against the Liquibase schema")
            .isEmpty()
    }

    @Test
    fun everyTableIsMapped() {
        val physical =
            transaction(db) {
                exec(
                    "select tablename from pg_tables where schemaname = 'public' " +
                        "and tablename not in ('databasechangelog', 'databasechangeloglock', 'shedlock')",
                ) { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toSet() }
            }

        assertThat(physical, name = "Liquibase tables").isEqualTo(ExposedSchema.ALL.map { it.tableName }.toSet())
    }
}
