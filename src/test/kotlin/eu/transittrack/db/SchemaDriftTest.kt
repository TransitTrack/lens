package eu.transittrack.db

import javax.sql.DataSource

import assertk.assertThat
import assertk.assertions.isEmpty
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
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
    @Autowired val dataSource: DataSource,
) : PostgresPerClassTest() {
    @Test
    fun exposedTablesMatchLiquibaseSchema() {
        val db = Database.connect(dataSource)
        val drift =
            try {
                transaction(db) {
                    MigrationUtils.statementsRequiredForDatabaseMigration(*ExposedSchema.ALL.toTypedArray(), withLogs = false)
                }
            } finally {
                TransactionManager.closeAndUnregister(db)
            }

        assertThat(drift.filterNot { it in IGNORED }, name = "DDL Exposed would run against the Liquibase schema").isEmpty()
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
            )
    }
}
