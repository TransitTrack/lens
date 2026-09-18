package eu.transittrack.config

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Gates a bean by deployment role, with monolith-by-default semantics: if no `role-*` Spring
 * profile is active at all, every role's beans are active (local dev, `./gradlew bootRun`,
 * every existing `@SpringBootTest` that sets no profile). Once one or more `role-*` profiles are
 * active, only matching beans are.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Conditional(RoleCondition::class)
annotation class ConditionalOnRole(
    val role: Role,
)

class RoleCondition : Condition {
    override fun matches(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata,
    ): Boolean {
        val attributes = metadata.getAnnotationAttributes(ConditionalOnRole::class.java.name) ?: return false
        val required = attributes["role"] as Role
        val activeProfiles = context.environment.activeProfiles.toSet()
        val anyRoleProfileActive = activeProfiles.any { it.startsWith("role-") }
        return !anyRoleProfileActive || activeProfiles.contains(required.profile)
    }
}
