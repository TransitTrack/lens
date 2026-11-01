package eu.transittrack.migration

import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val KEBAB = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
private val NUMBERED = Regex("^(\\d{4})-.*\\.yaml$")

/** Fails unless [name] is a lowercase kebab-case slug, the form used in changeset file names. */
fun requireKebabName(name: String) {
    require(KEBAB.matches(name)) {
        "Changeset name '$name' must be kebab-case (lowercase letters, digits, single dashes), e.g. add-vehicle-note"
    }
}

/** One more than the highest `NNNN-` prefix among the changesets in [changelogDir]. */
fun nextChangesetNumber(changelogDir: Path): Int =
    changelogDir
        .listDirectoryEntries()
        .mapNotNull {
            NUMBERED
                .matchEntire(it.name)
                ?.groupValues
                ?.get(1)
                ?.toInt()
        }.maxOrNull()
        ?.plus(1) ?: 1

fun formatChangesetNumber(number: Int): String = number.toString().padStart(4, '0')

/**
 * Renders one changeset wrapping each statement in a `sql:` change, in the layout of the
 * hand-written changesets. Statements always use a literal block (`|`), so characters YAML would
 * otherwise misread (`:`, `#`, quotes, leading `-`/`{`/`[`) need no escaping.
 */
fun renderChangeset(
    id: String,
    author: String,
    statements: List<String>,
): String =
    buildString {
        appendLine("databaseChangeLog:")
        appendLine("  - changeSet:")
        appendLine("      id: $id")
        appendLine("      author: $author")
        appendLine("      changes:")
        for (statement in statements) {
            appendLine("        - sql:")
            appendLine("            sql: |")
            for (line in statement.trimEnd().lines()) {
                appendLine(if (line.isEmpty()) "" else "              $line")
            }
        }
    }
