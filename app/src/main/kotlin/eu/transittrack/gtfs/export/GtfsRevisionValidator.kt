package eu.transittrack.gtfs.export

import java.nio.file.Files

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.validate.GtfsFeedLoader

@Serializable
data class ValidationNotice(
    val severity: String,
    val code: String,
    val message: String,
)

@Serializable
data class ValidationResult(
    val errorCount: Int,
    val warningCount: Int,
    val notices: List<ValidationNotice>,
    val publishable: Boolean,
)

/**
 * Serializes a revision to a temporary GTFS zip, runs gtfs-analyzer over it, persists a summary on
 * `gtfs_revision.last_validation`, and returns it. `publishable` (the analyzer's R1 verdict) is what
 * gates draft activation.
 *
 * This never throws for validation *findings* — an archive full of errors still returns a
 * [ValidationResult]. A fatal analyzer result is reported the same way: one `ERROR` notice and
 * `publishable = false`.
 */
@Service
class GtfsRevisionValidator(
    private val serializer: GtfsSerializer,
    private val feedLoader: GtfsFeedLoader,
    private val revisions: GtfsRevisionRepository,
    private val json: Json,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun validate(revisionId: Long): ValidationResult {
        val zip = Files.createTempFile("validate-$revisionId-", ".zip")
        try {
            Files.newOutputStream(zip).use { serializer.serialize(revisionId, it) }
            val report = feedLoader.load(zip)
            val result =
                ValidationResult(
                    errorCount = report.errorCount.toInt(),
                    warningCount = report.warningCount.toInt(),
                    notices = report.issues.map { ValidationNotice(it.severity.name, it.rule, it.sample) },
                    publishable = report.publishable,
                )
            // Persist the NORMALISED summary ({"errorCount":N,"warningCount":M,"notices":[...],
            // "publishable":B}) — DraftService.activate reads `publishable` from it and the web
            // app's draft pages read the rest.
            revisions.findById(revisionId)?.let {
                it.lastValidation = json.encodeToString(result)
                revisions.save(it)
            }
            log.info(
                "validated revision {} — {} error(s), {} warning(s), publishable={}",
                revisionId,
                report.errorCount,
                report.warningCount,
                report.publishable,
            )
            return result
        } finally {
            runCatching { Files.deleteIfExists(zip) }
        }
    }
}
