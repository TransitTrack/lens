package eu.transittrack.gtfs.support

import org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Slice-test meta-annotation for repository / persistence tests: a JDBC slice plus Exposed's
 * auto-configuration (its Spring transaction manager) and [ExposedSliceConfig].
 *
 * Runs against the shared Postgres container (see `eu.transittrack.support.PostgresContainerSupport`),
 * with Liquibase enabled. `NOT_SUPPORTED` overrides `@JdbcTest`'s default per-test rollback:
 * isolation between tests comes from `PostgresPerMethodTest`'s truncate instead, which a wrapped
 * test transaction would otherwise roll back along with the test's own changes. Every class annotated `@PostgresSliceTest` must
 * also extend `eu.transittrack.support.PostgresPerMethodTest` directly (annotations cannot
 * provide inherited behavior on their own).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@JdbcTest(properties = ["spring.liquibase.enabled=true"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(ExposedAutoConfiguration::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ExposedSliceConfig::class)
annotation class PostgresSliceTest
