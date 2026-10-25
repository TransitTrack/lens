package eu.transittrack.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * Excludes Spring GraphQL's own autoconfiguration (schema building, the `/graphql` endpoint,
 * GraphiQL) whenever a `role-*` profile is active and [Role.API] isn't one of them. The
 * `.graphqls` schema files are always on the classpath regardless of role, so without this,
 * Spring GraphQL's autoconfiguration still tries to build the schema under e.g. `role-predictor`
 * even though nothing in that role serves GraphQL requests.
 *
 * Runs as an [EnvironmentPostProcessor] (registered in `META-INF/spring.factories`), not a
 * `@Conditional`, because exclusion has to be decided before autoconfiguration classes are
 * evaluated. Mirrors [isRoleActive]'s monolith-by-default semantics so a monolith run (no `role-*`
 * profile) or a combined role (e.g. `role-api,role-feed-processor`) keeps GraphQL.
 */
class GraphQlAutoConfigurationRoleExclusion : EnvironmentPostProcessor {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        if (isRoleActive(Role.API, environment.activeProfiles.toSet())) {
            return
        }
        environment.propertySources.addFirst(
            MapPropertySource(
                "graphQlRoleExclusion",
                mapOf("spring.autoconfigure.exclude" to GRAPHQL_AUTOCONFIGURATION_CLASSES.joinToString(",")),
            ),
        )
    }

    companion object {
        private val GRAPHQL_AUTOCONFIGURATION_CLASSES =
            listOf(
                "org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration",
                "org.springframework.boot.graphql.autoconfigure.servlet.GraphQlWebMvcAutoConfiguration",
            )
    }
}
