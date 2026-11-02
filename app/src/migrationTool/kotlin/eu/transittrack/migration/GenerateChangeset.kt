package eu.transittrack.migration

import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import eu.transittrack.db.ExposedSchema

private const val CHANGELOG_DIR = "src/main/resources/db/changelog"
private const val MASTER = "db.changelog-master.yaml"

/**
 * Migrates a throwaway Postgres with the existing changesets, asks Exposed what DDL the table
 * objects in [ExposedSchema.ALL] still need, and writes it as the next numbered changeset.
 * Run with `./gradlew generateChangeset -Pname=<kebab-name>`.
 */
fun main(args: Array<String>) {
    val name = args.firstOrNull()
    if (name.isNullOrBlank()) {
        System.err.println("Missing changeset name: run ./gradlew generateChangeset -Pname=<kebab-name>")
        exitProcess(1)
    }
    try {
        requireKebabName(name)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        exitProcess(1)
    }

    val statements = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).use { postgres ->
        postgres.start()
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), database).update("")
        }
        val exposed = Database.connect(postgres.jdbcUrl, user = postgres.username, password = postgres.password)
        transaction(exposed) {
            MigrationUtils.statementsRequiredForDatabaseMigration(*ExposedSchema.ALL.toTypedArray(), withLogs = false)
        }.filterNot { it in ExposedSchema.IGNORED_DRIFT }
    }

    if (statements.isEmpty()) {
        println("Schema is up to date — no changeset written.")
        return
    }

    val dir = Path.of(CHANGELOG_DIR)
    val number = formatChangesetNumber(nextChangesetNumber(dir))
    val id = "$number-$name"
    dir.resolve("$id.yaml").writeText(renderChangeset(id, "transittrack", statements))
    dir.resolve(MASTER).appendText("  - include: { file: db/changelog/$id.yaml }\n")
    println("Wrote $CHANGELOG_DIR/$id.yaml (${statements.size} statement(s)) and added it to $MASTER. Review it before committing.")
}
