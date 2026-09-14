package eu.transittrack.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/** Started once per test JVM on first access; every test class shares this instance. */
object SharedPostgresContainer {
    val instance: PostgreSQLContainer by lazy {
        PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).apply { start() }
    }
}

/**
 * Shared connection wiring and truncate logic for every Postgres-backed test. Not extended
 * directly by test classes — use [PostgresPerMethodTest] or [PostgresPerClassTest].
 */
abstract class PostgresContainerSupport {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val tableNames: List<String> by lazy {
        jdbcTemplate
            .queryForList(
                """
                select tablename from pg_tables
                where schemaname = 'public'
                  and tablename not in ('databasechangelog', 'databasechangeloglock')
                """.trimIndent(),
                String::class.java,
            ).filterNotNull()
    }

    protected fun truncateAllTables() {
        if (tableNames.isEmpty()) return
        jdbcTemplate.execute("truncate table ${tableNames.joinToString(", ")} restart identity cascade")
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            val c = SharedPostgresContainer.instance
            registry.add("spring.datasource.url", c::getJdbcUrl)
            registry.add("spring.datasource.username", c::getUsername)
            registry.add("spring.datasource.password", c::getPassword)
        }
    }
}

/**
 * Extend this for a test class that needs a fresh (truncated) database per `@Test` method.
 *
 * [truncateBeforeEachTest] is `open` so a class whose Spring context kicks off async work at
 * context-startup time (e.g. a `@PostConstruct`-triggered background ingest reacting to
 * config-driven feed properties) can override it to wait for that work to settle *before*
 * truncating — otherwise the truncate can race an in-flight write the startup hook is still
 * making, which never happened when every test class had its own short-lived container.
 */
abstract class PostgresPerMethodTest : PostgresContainerSupport() {
    @BeforeEach
    open fun truncateBeforeEachTest() = truncateAllTables()
}

/**
 * Extend this for a `@TestInstance(Lifecycle.PER_CLASS)` test class whose `@BeforeAll` builds
 * fixture data shared read-only across its `@Test` methods. Call [truncateBeforeFixture] as the
 * first line of that `@BeforeAll` method yourself — it is not automatic, since JUnit does not
 * order a superclass's lifecycle callbacks ahead of a subclass's identically-annotated one in a
 * way this design can rely on.
 */
abstract class PostgresPerClassTest : PostgresContainerSupport() {
    protected fun truncateBeforeFixture() = truncateAllTables()
}
