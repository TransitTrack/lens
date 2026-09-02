package eu.transittrack.gtfs.validate

import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Paths
import java.time.LocalDate

import org.mobilitydata.gtfsvalidator.input.CountryCode
import org.mobilitydata.gtfsvalidator.input.DateForValidation
import org.mobilitydata.gtfsvalidator.input.GtfsInput
import org.mobilitydata.gtfsvalidator.model.NoticeReport
import org.mobilitydata.gtfsvalidator.notice.IOError
import org.mobilitydata.gtfsvalidator.notice.NoticeContainer
import org.mobilitydata.gtfsvalidator.notice.URISyntaxError
import org.mobilitydata.gtfsvalidator.table.GtfsFeedContainer
import org.mobilitydata.gtfsvalidator.table.GtfsFeedLoader
import org.mobilitydata.gtfsvalidator.util.ServiceIntervalCache
import org.mobilitydata.gtfsvalidator.validator.ClassGraphDiscovery
import org.mobilitydata.gtfsvalidator.validator.DefaultValidatorProvider
import org.mobilitydata.gtfsvalidator.validator.ValidationContext
import org.mobilitydata.gtfsvalidator.validator.ValidatorLoader
import org.mobilitydata.gtfsvalidator.validator.ValidatorLoaderException
import org.slf4j.LoggerFactory

/** The main entry point for running the validator against a GTFS input. */
class ValidationRunner {
    enum class Status {
        // Indicates validation successfully completed, but doesn't imply the
        // feed itself is valid.
        SUCCESS,

        // Indicates validation did not successfully complete, with exceptions
        // caught and written to the system errors JSON output.
        SYSTEM_ERRORS,

        // Indicates validation did not successfully complete, with exceptions
        // caught and written only to console logging.
        EXCEPTION,
    }

    data class ValidationResult(
        val status: Status,
        val feedContainer: GtfsFeedContainer? = null,
        val notices: Set<NoticeReport> = emptySet(),
    )

    fun validate(source: URI): ValidationResult {
        val validatorLoader: ValidatorLoader?
        try {
            validatorLoader = ValidatorLoader.createForClasses(
                ClassGraphDiscovery.discoverValidatorsInDefaultPackage(),
            )
        } catch (e: ValidatorLoaderException) {
            logger.error("Cannot load validator classes", e)
            return ValidationResult(Status.EXCEPTION)
        }
        val feedLoader = GtfsFeedLoader(ClassGraphDiscovery.discoverTables())

        val noticeContainer = NoticeContainer()
        val feedContainer: GtfsFeedContainer
        var gtfsInput: GtfsInput? = null
        try {
            gtfsInput = GtfsInput.createFromPath(Paths.get(source), noticeContainer)
        } catch (e: IOException) {
            logger.error("Cannot load GTFS feed", e)
            noticeContainer.addSystemError(IOError(e))
        } catch (e: URISyntaxException) {
            logger.error("Syntax error in URI", e)
            noticeContainer.addSystemError(URISyntaxError(e))
        }

        if (gtfsInput == null) {
            val status = if (noticeContainer.systemErrors.isNotEmpty()) {
                Status.SYSTEM_ERRORS
            } else {
                Status.EXCEPTION
            }
            val validationReport = noticeContainer.createValidationReport(noticeContainer.resolvedValidationNotices)

            return ValidationResult(status, notices = validationReport.notices)
        }

        val validationContext = ValidationContext
            .builder()
            .setCountryCode(CountryCode.forStringOrUnknown(CountryCode.ZZ))
            .set(ServiceIntervalCache::class.java, ServiceIntervalCache())
            .setDateForValidation(DateForValidation(LocalDate.now()))
            .build()
        try {
            feedContainer = loadAndValidate(
                validatorLoader,
                feedLoader,
                noticeContainer,
                gtfsInput,
                validationContext,
            )
        } catch (e: InterruptedException) {
            logger.error("Validation was interrupted", e)
            return ValidationResult(Status.EXCEPTION)
        }

        closeGtfsInput(gtfsInput, noticeContainer)

        val validationReport = noticeContainer.createValidationReport(noticeContainer.resolvedValidationNotices)

        // Output
        return ValidationResult(Status.SUCCESS, feedContainer, validationReport.notices)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(javaClass)

        /**
         * Closes a `GtfsInput`. Yields `IOError` if the `GtfsInput` could not be closed.
         *
         * @param gtfsInput the `GtfsInput` to close
         * @param noticeContainer the `NoticeContainer` that will contain the `IOError` if the
         *   `GtfsInput` could not be closed.
         */
        fun closeGtfsInput(
            gtfsInput: GtfsInput,
            noticeContainer: NoticeContainer,
        ) {
            try {
                gtfsInput.close()
            } catch (e: IOException) {
                logger.error("Cannot close GTFS input")
                noticeContainer.addSystemError(IOError(e))
            }
        }

        /**
         * Loads and validates GTFS feeds
         *
         * @param validatorLoader the `ValidatorLoader` used in the process
         * @param feedLoader the `GtfsFeedLoader` used in the process
         * @param noticeContainer the `NoticeContainer` that will contain `Notice`s related to the GTFS
         *   feed
         * @param gtfsInput the source of data
         * @param validationContext the `ValidationContext` do be used during validation
         * @return the `GtfsFeedContainer` used in the validation process
         * @throws InterruptedException if validation process was interrupted
         */
        @Throws(InterruptedException::class)
        fun loadAndValidate(
            validatorLoader: ValidatorLoader,
            feedLoader: GtfsFeedLoader,
            noticeContainer: NoticeContainer?,
            gtfsInput: GtfsInput?,
            validationContext: ValidationContext,
        ): GtfsFeedContainer {
            val validationProvider = DefaultValidatorProvider(validationContext, validatorLoader)
            val feedContainer: GtfsFeedContainer = feedLoader.loadAndValidate(
                gtfsInput,
                validationProvider,
                noticeContainer,
            )
            return feedContainer
        }
    }
}
