package eu.transittrack.gtfs.validate

import io.github.transittrack.gtfsanalyzer.EntityType
import io.github.transittrack.gtfsanalyzer.Notice
import io.github.transittrack.gtfsanalyzer.RuleClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import eu.transittrack.rest.generated.model.FindingSeverity
import io.github.transittrack.gtfsanalyzer.Severity as EngineSeverity

class ValidationFindingsTest {
    private fun notice(
        id: String,
        ruleId: String = "STM_014",
        severity: EngineSeverity = EngineSeverity.HIGH,
        entityType: EntityType = EntityType.Trip,
        entityId: String? = "T1",
        line: Long? = null,
    ) = Notice(
        id = id,
        ruleId = ruleId,
        severity = severity,
        ruleClass = RuleClass.ANALYTICS,
        entityType = entityType,
        entityId = entityId,
        line = line,
        file = "stop_times.txt",
        details = mapOf("distance_m" to "932"),
        title = "Excessive speed on a route segment",
        message = "message $id",
        remediation = "Check these two stops' times.",
        blocks = emptyList(),
        baseEffort = 1,
    )

    @Test
    fun `keeps only notices for the requested entity`() {
        val notices =
            listOf(
                notice("a"),
                notice("b", entityId = "T2"),
                notice("c", entityType = EntityType.Stop, entityId = "T1"),
            )

        val findings = notices.toFindings(entityType = "Trip", entityId = "T1")

        assertThat(findings.map { it.message }).containsExactly("message a")
    }

    @Test
    fun `entity type is matched case-insensitively and either filter may be omitted`() {
        val notices = listOf(notice("a"), notice("b", entityType = EntityType.Stop, entityId = "S1"))

        assertThat(notices.toFindings("trip", null).map { it.message }).containsExactly("message a")
        assertThat(notices.toFindings(null, "S1").map { it.message }).containsExactly("message b")
        assertThat(notices.toFindings(null, null)).hasSize(2)
    }

    @Test
    fun `most severe first, then by rule and row`() {
        val notices =
            listOf(
                notice("info", severity = EngineSeverity.INFO, ruleId = "A"),
                notice("low-b", severity = EngineSeverity.LOW, ruleId = "B", line = 2),
                notice("high", severity = EngineSeverity.CRITICAL, ruleId = "Z"),
                notice("low-a2", severity = EngineSeverity.LOW, ruleId = "A", line = 9),
                notice("low-a1", severity = EngineSeverity.LOW, ruleId = "A", line = 3),
            )

        val order = notices.toFindings(null, null).map { it.message }

        assertThat(order).containsExactly("message high", "message low-a1", "message low-a2", "message low-b", "message info")
    }

    @Test
    fun `counts findings per entity with the worst severity`() {
        val notices =
            listOf(
                notice("a", entityId = "T1", severity = EngineSeverity.LOW),
                notice("b", entityId = "T1", severity = EngineSeverity.CRITICAL),
                notice("c", entityId = "T2", severity = EngineSeverity.INFO),
                notice("d", entityType = EntityType.Stop, entityId = "S1"),
                notice("e", entityId = null),
            )

        val counts = notices.countsByEntity("trip")

        assertThat(counts.map { it.entityId }).containsExactly("T1", "T2")
        assertThat(counts.first { it.entityId == "T1" }.count).isEqualTo(2)
        assertThat(counts.first { it.entityId == "T1" }.worstSeverity).isEqualTo(FindingSeverity.CRITICAL)
        assertThat(counts.first { it.entityId == "T2" }.worstSeverity).isEqualTo(FindingSeverity.INFO)
    }

    @Test
    fun `carries the analyzer detail through unchanged`() {
        val finding = listOf(notice("a", line = 12)).toFindings("Trip", "T1").single()

        assertThat(finding.ruleId).isEqualTo("STM_014")
        assertThat(finding.severity).isEqualTo(FindingSeverity.HIGH)
        assertThat(finding.ruleClass).isEqualTo("ANALYTICS")
        assertThat(finding.entityType).isEqualTo("Trip")
        assertThat(finding.file).isEqualTo("stop_times.txt")
        assertThat(finding.line).isEqualTo(12)
        assertThat((finding.details as Map<*, *>)["distance_m"]).isEqualTo("932")
        assertThat(finding.remediation).isEqualTo("Check these two stops' times.")
    }
}
