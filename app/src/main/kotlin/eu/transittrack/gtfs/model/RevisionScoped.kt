package eu.transittrack.gtfs.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.db.RowTable
import eu.transittrack.geo.BBox
import eu.transittrack.gtfs.revision.GtfsRevisionTable

/**
 * Base class for all revision-scoped GTFS entities.
 *
 * Every subclass is scoped to one `gtfs_revision`, so `revisionId` lives here rather than being
 * re-declared on each entity. Subclasses take it as a pass-through constructor parameter: `class
 * Foo(revisionId: Long, ...) : RevisionScoped(revisionId)`.
 *
 * `id` is assigned from the `gtfs_entity_seq` sequence (see [RevisionScopedTable.id]).
 */
abstract class RevisionScoped(
    var revisionId: Long,
) {
    var id: Long? = null
}

/**
 * Base table for every [RevisionScoped] row type: the `id` drawn from `gtfs_entity_seq` and the
 * `revision_id` FK to `gtfs_revision` (cascading delete, named [revisionFkName]). [rowClass] is the
 * row type this table stores, so generic writers can route a row to its table by runtime class.
 */
abstract class RevisionScopedTable<R : RevisionScoped>(
    name: String,
    val rowClass: Class<R>,
    revisionFkName: String,
) : RowTable<R>(name) {
    val id = long("id").autoIncrement(Sequence("gtfs_entity_seq", startWith = 1, incrementBy = 500))
    val revisionId =
        long("revision_id").references(
            GtfsRevisionTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = revisionFkName,
        )

    override val primaryKey = PrimaryKey(id, name = "${name}_pkey")

    /** Sets [RevisionScoped.id] (not a constructor parameter) on a row built by [read]. */
    protected fun <T : R> T.withId(row: ResultRow): T = also { it.id = row[this@RevisionScopedTable.id] }
}

/**
 * The four nullable bounding-box columns an [BBox] is flattened into: no extent is stored as four
 * NULLs and read back as `null`.
 */
class ExtentColumns(
    table: Table,
) {
    val minLat = with(table) { double("min_lat").nullable() }
    val minLon = with(table) { double("min_lon").nullable() }
    val maxLat = with(table) { double("max_lat").nullable() }
    val maxLon = with(table) { double("max_lon").nullable() }

    fun read(row: ResultRow): BBox? {
        val south = row[minLat] ?: return null
        val north = row[maxLat] ?: return null
        val west = row[minLon] ?: return null
        val east = row[maxLon] ?: return null
        return BBox(west, south, east, north)
    }

    fun bind(
        s: UpdateBuilder<*>,
        e: BBox?,
    ) {
        s[minLat] = e?.minLat
        s[minLon] = e?.minLng
        s[maxLat] = e?.maxLat
        s[maxLon] = e?.maxLng
    }
}

/**
 * Shared query surface for every [RevisionScoped] table. Concrete repositories extend it with their
 * own explicit queries.
 *
 * Annotated `@Transactional` itself (and every member `open`): Spring takes a class-level
 * `@Transactional` from the class that declares the method, so the subclasses' annotation does not
 * cover these inherited members, and a CGLIB proxy cannot route final members to the target.
 */
@Transactional
open class RevisionScopedRepository<R : RevisionScoped>(
    open val table: RevisionScopedTable<R>,
) {
    open fun findByRevisionId(revisionId: Long): List<R> =
        table
            .selectAll()
            .where { table.revisionId eq revisionId }
            .map(table::read)

    open fun deleteByRevisionId(revisionId: Long): Int = table.deleteWhere { table.revisionId eq revisionId }

    open fun findById(id: Long): R? =
        table
            .selectAll()
            .where { table.id eq id }
            .singleOrNull()
            ?.let(table::read)

    open fun findAllById(ids: Iterable<Long>): List<R> =
        table
            .selectAll()
            .where { table.id inList ids.toList() }
            .map(table::read)

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    open fun save(row: R): R {
        val id = row.id
        if (id == null) {
            row.id = table.insert { table.bind(it, row) }[table.id]
        } else {
            table.update({ table.id eq id }) { table.bind(it, row) }
        }
        return row
    }

    open fun saveAll(rows: Iterable<R>): List<R> = rows.map(::save)

    open fun delete(row: R) {
        val id = row.id ?: return
        table.deleteWhere { table.id eq id }
    }

    open fun deleteAll(rows: Iterable<R>) {
        val ids = rows.mapNotNull { it.id }
        if (ids.isEmpty()) return
        table.deleteWhere { table.id inList ids }
    }
}
