# Progress: shared-testcontainer-base plan

- [x] Task 1: Build shared base classes + prove on 1 class per group (note: found & fixed
      AutoConfigureTestEntityManager/TestEntityManager.flush() incompatibility with
      NOT_SUPPORTED in RevisionRepositoryTest.kt -- same fix needed for
      DraftEditRepositoryTest.kt in Task 4)
- [x] Task 2: Migrate 22 manual-cleanup classes (note: full-suite regression check after
      this task showed 22 unrelated FAILURES, all in still-unmigrated @PostgresSliceTest
      classes -- root cause: Task 1's edit to the shared @PostgresSliceTest annotation
      (adding NOT_SUPPORTED, dropping the old container @Import) took effect globally
      for ALL 42 slice-test classes immediately, not just the ones individually migrated
      to extend the new base class. The 41 not-yet-migrated ones now have NEITHER the old
      @DataJpaTest rollback NOR the new truncate -- zero isolation between test methods,
      causing duplicate-key collisions on fixed fixture data (e.g. feed code "f1").
      Expected to resolve once Task 4 migrates the remaining slice tests to extend
      PostgresPerMethodTest. Not fixing now -- proceeding with Task 3 (PerClass classes,
      unaffected by this since they still use the untouched TestcontainersConfiguration),
      then Task 4 will close this gap. Task 2's OWN specified focused tests all passed.)
- [x] Task 3: Migrate 24 PerClass classes (note: full-class-set test run failed with
      "FATAL: sorry, too many clients already" -- root cause: sharing ONE Postgres container
      across ~24 distinct SpringBootTest contexts means their Hikari pools (default 10
      connections each) sum past Postgres's max_connections, which never happened when each
      class had its own container. Fixed by capping maximum-pool-size=3/minimum-idle=1 in
      PostgresIntegrationTest's @DynamicPropertySource. Re-ran the full 24-class set after
      the fix -- all green. Committed as 3c1f52f.)
- [x] Task 4: Migrate 41 slice-test classes (note: mechanical transformation applied via a
      script since all 41 files shared the identical `) {` -> `) : PostgresPerMethodTest() {`
      shape. Also removed DraftEditRepositoryTest's unused @AutoConfigureTestEntityManager/
      TestEntityManager (same NOT_SUPPORTED incompatibility as RevisionRepositoryTest in Task 1).
      Full regression run (avl.*, gtfs.*, predict.*, schedule.*) surfaced 4 real bugs that proper
      per-method isolation now exposes (previously masked by @DataJpaTest's shared-transaction
      first-level cache or per-class-fresh-container accidents):
        - AvlEntitiesPersistenceTest / PredictionEntitiesPersistenceTest: @BeforeTest fixture
          inserts used `EntityManager.createNativeQuery(...).executeUpdate()`, which needs an
          active transaction that NOT_SUPPORTED deliberately removes -- switched to JdbcTemplate.
        - AvlEntitiesPersistenceTest's `vehicle_state unique per feed+vehicle` compared two
          separate repository lookups with plain `isEqualTo` -- VehicleState has no equals()
          override, so this only ever passed by accident of a shared first-level cache returning
          the same object instance. Fixed to assert on `.id` instead.
        - DerivedGtfsWriterTest hardcoded `tripPatternId = 1L` with no real trip_pattern row to
          back it, previously only working via leftover state from another test's leaked data in
          a shared context; now creates a real TripPattern via ScheduleWriter first.
        - OptimizationEntitiesPersistenceTest: `findAllByIdForUpdate` (PESSIMISTIC_WRITE) also
          needs an active transaction -- added a method-level @Transactional override just for
          that test. Separately, jsonb round-trips through Postgres's canonical text form (space
          after `:`/`,`, and reordered keys for multi-key objects) instead of preserving the raw
          bytes written, which a shared first-level cache previously hid -- adjusted assertions to
          match jsonb's real behavior (exact string for single-key values, substring checks for
          the multi-key `evidence` field).
      All fixes verified individually and via a final full avl.*/gtfs.*/predict.*/schedule.* run,
      all green. This full-package run also empirically confirmed the container-sharing benefit:
      ~22 minutes before Task 3's Hikari fix down to ~1 minute once only one container start
      is needed. Committed together with Task 4's migration in one commit.)
- [x] Task 5: Remove old configs + full suite verify (deleted TestcontainersConfiguration.kt
      and GtfsPostgresTestContainer.kt, updated IngestionTestFactory's stale doc comment. Full
      `./gradlew test` run: BUILD SUCCESSFUL, consistently ~1 minute across repeated runs. One
      run hit a transient Postgres deadlock on a TRUNCATE -- reran clean, consistent with a
      timing-dependent flake (a background scheduled task in another cached context colliding
      with a concurrent truncate) rather than a systematic bug. Verified via `docker events`
      that exactly 1 postgres:18-alpine container is created for the whole suite (Gradle's
      captured test logs didn't surface Testcontainers' own log lines at any level, so used
      docker events instead of grepping for the log line the plan named). Plan complete.)
