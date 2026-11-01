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

        assertThat(drift.filterNot { it in IGNORED }, name = "DDL Exposed would run against the Liquibase schema").isEmpty()
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

    companion object {
        /**
         * Exact statements that are known, accepted differences between the Exposed model and the
         * Liquibase schema. Every entry needs a comment saying why it is safe to ignore.
         */
        val IGNORED: Set<String> =
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
}
