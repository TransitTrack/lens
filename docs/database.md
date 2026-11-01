# Database

PostgreSQL, accessed through [JetBrains Exposed](https://github.com/JetBrains/Exposed) (DSL, no ORM
entities). [Liquibase](https://www.liquibase.com/) owns and migrates the schema; the changesets stay
YAML under `src/main/resources/db/changelog/` and are listed in `db.changelog-master.yaml`.

## Tables are the source of truth

The Exposed table objects (`AvlFeedTable`, `GtfsRevisionTable`, ...) describe the schema the code
expects. `eu.transittrack.db.ExposedSchema.ALL` lists every one of them; a new table must be added
there. A changeset is never edited once committed (0001-0020 included): a schema change is always a
new, higher-numbered changeset.

### Changing the schema: generate, review, edit, commit

1. Change the table object (add a column, an index, a table, ...).
2. Generate the changeset:

   ```
   ./gradlew generateChangeset -Pname=<kebab-name>
   ```

   The task starts a throwaway `postgres:18-alpine` (Testcontainers, so Docker must be running),
   migrates it with the existing changesets, asks Exposed
   (`MigrationUtils.statementsRequiredForDatabaseMigration`) what DDL is still missing, drops the
   accepted differences in `ExposedSchema.IGNORED_DRIFT`, and writes the rest as
   `src/main/resources/db/changelog/<NNNN>-<name>.yaml` (next free number) plus an `include` line in
   the master. If nothing is missing it prints `Schema is up to date` and writes nothing. `-Pname` is
   required and must be kebab-case (`add-vehicle-note`).
3. Review and edit the generated file. Each DDL statement is a `sql:` change in a literal block.
   Exposed's DDL is a starting point: add data backfills, `NOT NULL` after backfill, `CONCURRENTLY`
   indexes, a `rollback:` block, or rewrite a statement as a native Liquibase change where that reads
   better.
4. Commit the table change and the changeset together.

The generator code lives in the `migrationTool` source set (`src/migrationTool/kotlin`), outside the
application classpath.

### Drift test

`SchemaDriftTest` migrates a Testcontainers Postgres with Liquibase and fails when
`MigrationUtils.statementsRequiredForDatabaseMigration` still wants to run DDL, printing it.
`everyTableIsMapped` fails when Liquibase creates a table (other than `databasechangelog*` and
`shedlock`) that is not in `ExposedSchema.ALL`, and vice versa.

`ExposedSchema.IGNORED_DRIFT` holds the exact statements that are accepted differences (today the
changeset-0012 `default nextval('<table>_seq')` id columns, which Exposed cannot model; inserts name
the sequence explicitly). Both the test and the generator use it. Every entry needs a comment saying
why it is safe to ignore.

## Raw SQL allowlist

Everything goes through the Exposed DSL except the following. A new raw `exec(...)` needs a line here
and a reason the DSL cannot express it.

| Constant | File | Why raw SQL |
| --- | --- | --- |
| `AGGREGATE_ACCURACY_SQL` (`PredictionAccuracyRepository.aggregateAccuracy`) | `predict/model/PredictionAccuracy.kt` | CTEs, aggregate `FILTER`, `percentile_cont ... WITHIN GROUP` and `GROUPING SETS`; run via `exec` with bound parameters, one `?` per algorithm. |

(`grep -rn "exec(" src/main` also finds `db/SqlExpressions.kt`'s `execBatch`, which runs DSL
statements, not a SQL string.)

## Custom SQL expressions

Where Exposed has no builder, a small `Expression`, `CustomFunction` or `Statement` renders the SQL
with every value a bound parameter (no string-built values).

| Name | File | Renders |
| --- | --- | --- |
| `EpochSecondsBetween` | `db/SqlExpressions.kt` | `extract(epoch from <until> - <since>)`, seconds between two `timestamptz` expressions. |
| `InsertSelectOnConflictDoNothing` | `db/SqlExpressions.kt` | `INSERT ... SELECT ... ON CONFLICT (<columns>) DO NOTHING`; Exposed's `insertIgnore` omits the conflict target. Column identifiers only. |
| `JdbcTransaction.execBatch` / `SameSqlBatch` | `db/SqlExpressions.kt` | Runs same-SQL DSL statements (updates, insert-selects) as one JDBC batch. |
| `TimestampTzParam` | `avl/model/VehicleState.kt` | `CAST(? AS TIMESTAMP WITH TIME ZONE)`, an `Instant` parameter typed for timestamp arithmetic. |
| `blankToNull` (`CustomFunction`) | `schedule/derive/DerivedGtfsWriter.kt` | `NULLIF(value, '')`. |
| `pg_advisory_xact_lock(hashtext(?), hashtext(?))` (`CustomFunction`) | `predict/PredictionWriter.kt` | Transaction-scoped advisory lock keyed by namespace and key. |
| `greatest(<column>, EXCLUDED.<column>)` (`CustomFunction`) | `predict/PredictionWriter.kt` | Upsert merge keeping the larger value. |
| `AbsFunction(EpochSecondsBetween(...))` | `predict/PredictionWriter.kt` | `ABS(extract(epoch ...))` in the prediction conflict `WHERE`. |
| `now()` default (`CustomFunction`) | `db/RowTable.kt` | `Column<Instant>.defaultNow()`. |

## Transaction rules

- Repositories and writers are class-level `@Transactional` (propagation `REQUIRED`): they join the
  caller's transaction. Exposed runs inside the Spring-managed transaction
  (`SpringTransactionManager`), so there is one connection per Spring transaction.
- `REQUIRES_NEW` is used where a write must stand independently of the caller's transaction (it
  commits on its own connection, so locks taken in the outer transaction are not shared):
  - `avl/ingest/AvlWriter` (all methods): reports and matches written by `AvlIngestService.pollOnce`
    stand even if the poll transaction rolls back.
  - `avl/ingest/AvlPollRecorder`: `last_poll_*` is persisted even when the poll fails and rolls back
    (a separate bean, so the proxy applies).
  - `avl/feed/AvlFeedCleanupListener`: after-commit cleanup of AVL feeds needs a fresh transaction;
    the committed one is being torn down.
  - `schedule/derive/ScheduleWriter` and `schedule/derive/DerivedGtfsWriter` (all methods): derivation
    runs with no caller transaction, and draft discard / revert rely on deletes standing after a
    rollback.
  - `gtfs/store/ExposedRevisionWriter.deleteAllForRevision`: the wipe in `DraftService.revertToFork`
    must stand even if the re-copy rolls back.
  - `gtfs/revision/RevisionService` (`markRevertFailed`): records the "empty draft, must re-revert"
    state so it survives the caller's rollback.
  - `predict/PredictionWriter.inTransaction`: each matched report is written in its own transaction
    so writes stand or fall alone, and its row and advisory locks end with it.
  - ShedLock is not `REQUIRES_NEW` but behaves alike: `ExposedLockProvider` runs every lock/unlock
    in a top-level transaction on its own connection, so locks commit independently of any surrounding
    transaction.
- Timestamps: declare `timestamptz` columns with `RowTable.timestampTz()`, never Exposed's
  `timestamp()`. `timestamp()` binds through a `java.sql.Timestamp` built from the JVM zone's local
  time, which shifts instants by an hour inside a DST fall-back overlap. `timestampTz` binds a UTC
  `OffsetDateTime`.
- Errors: `ExposedExceptionTranslationAspect` converts `ExposedSQLException` (and commit-time
  `TransactionSystemException`s wrapping a `SQLException`) into Spring `DataAccessException`s at every
  `@Transactional` boundary, so callers keep catching `DataIntegrityViolationException`,
  `DuplicateKeyException`, and so on.
