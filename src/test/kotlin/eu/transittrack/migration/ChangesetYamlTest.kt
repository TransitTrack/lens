package eu.transittrack.migration

import java.nio.file.Path
import kotlin.io.path.createFile

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ChangesetYamlTest {
    @Test
    fun rendersOneSqlChangePerStatement() {
        val yaml =
            renderChangeset(
                id = "0021-add-note",
                author = "transittrack",
                statements = listOf("ALTER TABLE gtfs_feed ADD note TEXT NULL", "CREATE INDEX ix_a ON t (a: b)\nWHERE a > 1"),
            )

        assertThat(yaml).isEqualTo(
            """
            databaseChangeLog:
              - changeSet:
                  id: 0021-add-note
                  author: transittrack
                  changes:
                    - sql:
                        sql: |
                          ALTER TABLE gtfs_feed ADD note TEXT NULL
                    - sql:
                        sql: |
                          CREATE INDEX ix_a ON t (a: b)
                          WHERE a > 1

            """.trimIndent(),
        )
    }

    @Test
    fun nextNumberAfter0020Is0021(
        @TempDir dir: Path,
    ) {
        dir.resolve("0001-a.yaml").createFile()
        dir.resolve("0020-b.yaml").createFile()
        dir.resolve("db.changelog-master.yaml").createFile()

        val next = nextChangesetNumber(dir)

        assertThat(next).isEqualTo(21)
        assertThat(formatChangesetNumber(next)).isEqualTo("0021")
    }

    @Test
    fun rejectsNonKebabName() {
        assertFailure { requireKebabName("Add_Note") }.messageContains("kebab")
        assertFailure { requireKebabName("") }.messageContains("kebab")
        assertFailure { requireKebabName("-x") }.messageContains("kebab")
        requireKebabName("add-note-2")
    }
}
