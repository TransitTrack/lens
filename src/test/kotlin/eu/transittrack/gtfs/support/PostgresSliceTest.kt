package eu.transittrack.gtfs.support

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Slice-test meta-annotation for GTFS repository / JPA tests.
 *
 * Runs against the shared Postgres container (see `eu.transittrack.support.PostgresContainerSupport`),
 * with Liquibase enabled and Hibernate `ddl-auto: validate`. `NOT_SUPPORTED` overrides
 * `@DataJpaTest`'s default per-test rollback: isolation between tests comes from
 * `PostgresPerMethodTest`'s truncate instead, which a wrapped test transaction would otherwise
 * roll back along with the test's own changes. Every class annotated `@PostgresSliceTest` must
 * also extend `eu.transittrack.support.PostgresPerMethodTest` directly (annotations cannot
 * provide inherited behavior on their own).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@DataJpaTest(properties = ["spring.liquibase.enabled=true"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
annotation class PostgresSliceTest
