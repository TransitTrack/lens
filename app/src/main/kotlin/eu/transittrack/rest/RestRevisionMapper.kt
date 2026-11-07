package eu.transittrack.rest

import org.springframework.stereotype.Component

import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.validate.LoadValidationReport
import eu.transittrack.rest.generated.model.Revision
import eu.transittrack.rest.generated.model.RevisionStatus
import eu.transittrack.rest.generated.model.Severity
import eu.transittrack.rest.generated.model.ValidationNotice
import eu.transittrack.rest.generated.model.ValidationSummary

@Component
class RestRevisionMapper {
    fun map(
        revision: GtfsRevision,
        feedCode: String,
    ): Revision {
        val summary = revision.validationReport?.let { LoadValidationReport.fromJson(it) }?.let { report ->
            ValidationSummary(
                Math.toIntExact(report.errorCount),
                Math.toIntExact(report.warningCount),
                report.issues.map { ValidationNotice(it.rule, Severity.valueOf(it.severity.name), Math.toIntExact(it.count), it.sample) },
            )
        }
        return Revision(
            id = revision.id.toString(), feedCode = feedCode, status = RevisionStatus.valueOf(revision.status.name),
            createdAt = revision.createdAt.toString(), filesPresent = revision.filesPresent,
            activatedAt = revision.activatedAt?.toString(), supersededAt = revision.supersededAt?.toString(),
            contentSha256 = revision.contentSha256, byteSize = revision.byteSize?.let(Math::toIntExact),
            rowCounts = revision.rowCounts, feedStartDate = revision.feedStartDate?.toString(),
            feedEndDate = revision.feedEndDate?.toString(), validationSummary = summary, errorMessage = revision.errorMessage,
        )
    }
}
