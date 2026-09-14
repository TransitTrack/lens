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
- [ ] Task 3: Migrate 24 PerClass classes
- [ ] Task 4: Migrate 41 slice-test classes
- [ ] Task 5: Remove old configs + full suite verify
