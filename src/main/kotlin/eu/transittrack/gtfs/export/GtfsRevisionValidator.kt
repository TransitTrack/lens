package eu.transittrack.gtfs.export

import java.nio.file.Files

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.validate.GtfsFeedLoader

data class ValidationNotice(
    val severity: String,
    val code: String,
    val message: String,
)

data class ValidationResult(
    val errorCount: Int,
    val warningCount: Int,
    val notices: List<ValidationNotice>,
)

/**
 * Serializes a revision to a temporary GTFS zip, runs the canonical MobilityData validator over it,
 * persists the raw report on `gtfs_revision.last_validation`, and returns a mapped summary.
 *
 * This never throws for validation *findings* — an archive full of errors still returns a
 * [ValidationResult]. It only propagates [eu.transittrack.gtfs.validate.GtfsValidationException] when
 * the validator itself fails to run (a system error), which callers treat as a failed job.
 */
@Service
class GtfsRevisionValidator(
    private val serializer: GtfsSerializer,
    private val feedLoader: GtfsFeedLoader,
    private val revisions: GtfsRevisionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun validate(revisionId: Long): ValidationResult {
        val zip = Files.createTempFile("validate-$revisionId-", ".zip")
        try {
            Files.newOutputStream(zip).use { serializer.serialize(revisionId, it) }
            val report = feedLoader.load(zip)
            revisions.findById(revisionId).ifPresent {
                it.lastValidation = report.toJson()
                revisions.save(it)
            }
            log.info(
                "validated revision {} — {} error(s), {} warning(s)",
                revisionId,
                report.errorCount,
                report.warningCount,
            )
            return ValidationResult(
                errorCount = report.errorCount.toInt(),
                warningCount = report.warningCount.toInt(),
                notices = report.issues.map { ValidationNotice(it.severity.name, it.rule, it.sample) },
            )
        } finally {
            runCatching { Files.deleteIfExists(zip) }
        }
    }
}
