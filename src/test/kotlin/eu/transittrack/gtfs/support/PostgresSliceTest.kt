package eu.transittrack.gtfs.support

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import

/**
 * Slice-test meta-annotation for GTFS repository / JPA tests.
 *
 * Runs against a real Postgres (Testcontainers via `@ServiceConnection`), with
 * Liquibase enabled and Hibernate `ddl-auto: validate`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@DataJpaTest(properties = ["spring.liquibase.enabled=true"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(GtfsPostgresTestContainer::class)
annotation class PostgresSliceTest
