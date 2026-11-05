package eu.transittrack.gtfs.ingest

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createTempDirectory

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.annotation.AnnotationAwareOrderComparator
import org.springframework.stereotype.Service

import eu.transittrack.GtfsProperties
import eu.transittrack.ParseException
import eu.transittrack.geo.algorithm.haversineDistance
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.model.Location
import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.model.Shape
import eu.transittrack.gtfs.model.ShapePoint
import eu.transittrack.gtfs.model.ShapeRepository
import eu.transittrack.gtfs.parse.GtfsCsv
import eu.transittrack.gtfs.parse.GtfsFileDef
import eu.transittrack.gtfs.parse.GtfsFileRegistry
import eu.transittrack.gtfs.parse.GtfsGeoJsonReader
import eu.transittrack.gtfs.parse.GtfsZip
import eu.transittrack.gtfs.parse.MissingRequiredFieldException
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.validate.GtfsFeedLoader
import eu.transittrack.gtfs.validate.GtfsValidationException
import eu.transittrack.schedule.derive.DerivationService
import eu.transittrack.util.observability.TransitTrackMetrics

interface IngestionPostProcessor {
    fun postProcess(revisionId: Long): Any

    /** Undo whatever [postProcess] wrote — called when a later pipeline step fails the revision. */
    fun onIngestionFailure(revisionId: Long) {}
}

class IngestionFailedEvent(
    val revisionId: Long,
) : ApplicationEvent("ingestion_service")

class IngestionFinishedEvent(
    val revisionId: Long,
) : ApplicationEvent("ingestion_service")

/**
 * Orchestrates the GTFS ingestion pipeline: download -> unchanged-check -> validate (gtfs-analyzer
 * on the archive; the report is stored, then the revision fails on a fatal analyzer error, on a
 * required file the analyzer couldn't read, or — in strict mode — when the feed isn't publishable)
 * -> stream each registry file out of the zip & bulk-write in registry order (rows missing a
 * required key are skipped) -> shape aggregates -> derive dates -> DERIVING (the `eu.transittrack.schedule` model, unless `transittrack.schedule.enabled` is
 * false) -> READY -> (auto-)activate -> prune.
 *
 * [ingest] runs the pipeline on the ingest executor and returns immediately; [ingestBlocking] runs
 * it inline. Every failure is funneled to [RevisionService.fail], the revision's derived schedule
 * rows are wiped, and the scratch directory is always deleted.
 */
@Service
class IngestionService(
    private val downloader: FeedDownloader,
    private val revisionService: RevisionService,
    private val writer: RevisionWriter,
    private val feeds: GtfsFeedRepository,
    private val feedService: GtfsFeedService,
    private val props: GtfsProperties,
    private val shapes: ShapeRepository,
    private val feedLoader: GtfsFeedLoader,
    private val gtfsIngestExecutor: org.springframework.core.task.TaskExecutor,
    private val eventPublisher: ApplicationEventPublisher,
    private val derivationService: DerivationService,
    postProcessors: ObjectProvider<IngestionPostProcessor>,
    private val metrics: TransitTrackMetrics = TransitTrackMetrics.forTests(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val postProcessors: List<IngestionPostProcessor> = postProcessors
        .sortedWith(AnnotationAwareOrderComparator.INSTANCE)

    /**
     * Starts an ingest and returns the freshly created PENDING revision immediately; the pipeline
     * runs on [gtfsIngestExecutor]. Use [ingestBlocking] when the caller needs the finished revision
     * (tests, CLI).
     */
    fun ingest(feedCode: String): GtfsRevision {
        val revision = openRevision(feedCode)
        metrics.gtfsImportStarted(feedCode)
        gtfsIngestExecutor.execute {
            runPipeline(revision.id!!)
        }
        return revision
    }

    /** Synchronous ingest: runs the pipeline inline and returns the finished revision. */
    fun ingestBlocking(feedCode: String): GtfsRevision {
        val revision = openRevision(feedCode)
        metrics.gtfsImportStarted(feedCode)
        runPipeline(revision.id!!)
        return revisionService.revision(revision.id!!)
    }

    /** Feed lookup + enabled/in-progress guards + PENDING revision creation. */
    private fun openRevision(feedCode: String): GtfsRevision {
        val feed = feeds.findByCode(feedCode) ?: throw IllegalArgumentException("no feed '$feedCode'")
        require(feed.enabled) { "feed '$feedCode' is disabled" }
        check(!revisionService.hasInProgress(feed.id!!)) {
            "feed '$feedCode' already has an ingest in progress"
        }
        return revisionService.createPending(feed.id!!, feed.url)
    }

    fun runPipeline(revisionId: Long) {
        val feedId = revisionService.revision(revisionId).feedId
        val feed = feeds.findById(feedId) ?: throw NoSuchElementException("no feed $feedId")
        val tempDir = createTempDirectory(tempRoot(), "gtfs-$revisionId-")
        val zipPath = tempDir.resolve("feed.zip")
        val startedAt = Instant.now()
        try {
            log.info("Starting download of ${feed.code}")
            revisionService.transition(revisionId, GtfsRevisionStatus.DOWNLOADING)
            val downloadStartedAt = Instant.now()
            val dl =
                runCatching { downloader.download(feed.url, zipPath) }
                    .onSuccess {
                        metrics
                            .gtfsStageFinished(
                                feed.code, TransitTrackMetrics.ImportStage.DOWNLOAD, TransitTrackMetrics.Outcome.SUCCESS,
                                Duration
                                    .between(downloadStartedAt, Instant.now()),
                            )
                    }.onFailure {
                        metrics
                            .gtfsStageFinished(
                                feed.code, TransitTrackMetrics.ImportStage.DOWNLOAD, TransitTrackMetrics.Outcome.FAILED,
                                Duration
                                    .between(downloadStartedAt, Instant.now()),
                            )
                    }.getOrThrow()
            metrics.gtfsDownloadBytes(feed.code, dl.byteSize)

            val activeSha =
                revisionService.activeRevisionId(feed.id!!)?.let {
                    revisionService.revision(it).contentSha256
                }
            if (activeSha != null && activeSha == dl.sha256) {
                revisionService.transition(revisionId, GtfsRevisionStatus.DOWNLOADING) {
                    it.contentSha256 = dl.sha256
                    it.byteSize = dl.byteSize
                }
                revisionService.markUnchanged(revisionId)
                feedService.markIngested(feed.id!!)
                metrics
                    .gtfsImportFinished(feed.code, TransitTrackMetrics.ImportOutcome.UNCHANGED, Duration.between(startedAt, Instant.now()))
                log.info("Finished download and detected no changes for ${feed.code}")
                return
            }

            log.info("Starting validation of ${feed.code}")
            revisionService.transition(revisionId, GtfsRevisionStatus.VALIDATING) {
                it.contentSha256 = dl.sha256
                it.byteSize = dl.byteSize
            }

            val validationStartedAt = Instant.now()
            val loadReport =
                runCatching { feedLoader.load(zipPath, feed.code) }
                    .onSuccess {
                        metrics
                            .gtfsStageFinished(
                                feed.code, TransitTrackMetrics.ImportStage.VALIDATE, TransitTrackMetrics.Outcome.SUCCESS,
                                Duration
                                    .between(validationStartedAt, Instant.now()),
                            )
                    }.onFailure {
                        metrics
                            .gtfsStageFinished(
                                feed.code, TransitTrackMetrics.ImportStage.VALIDATE, TransitTrackMetrics.Outcome.FAILED,
                                Duration
                                    .between(validationStartedAt, Instant.now()),
                            )
                    }.getOrThrow()
            revisionService.transition(revisionId, GtfsRevisionStatus.VALIDATING) {
                it.validationReport = loadReport.toJson()
            }
            val missingRequired = loadReport.unavailableFiles.filter { it in GtfsFileRegistry.requiredFiles }
            val stopReason =
                when {
                    loadReport.fatal != null -> "fatal analyzer error: ${loadReport.fatal!!.message}"
                    missingRequired.isNotEmpty() -> "required file(s) unreadable: $missingRequired"
                    props.ingest.strictValidation && !loadReport.publishable -> "strict mode: feed is not publishable"
                    else -> null
                }
            if (stopReason != null) {
                log.warn("Validation failed for ${feed.code} ($stopReason); ${loadReport.errorCount} error(s)")
                throw GtfsValidationException(loadReport)
            }

            log.info("Validation successful -> Starting processing of ${feed.code}")
            revisionService.transition(revisionId, GtfsRevisionStatus.PARSING)

            val rowCounts = LinkedHashMap<String, Long>()
            val filesPresent =
                GtfsZip(zipPath).use { zip ->
                    for (def in GtfsFileRegistry.orderedForParsing) {
                        if (def.fileName !in zip.fileNames || def.fileName in loadReport.unavailableFiles) {
                            continue
                        }

                        rowCounts[def.entityType] =
                            when (def.kind) {
                                GtfsFileDef.Kind.CSV -> loadCsv(revisionId, zip, def)
                                GtfsFileDef.Kind.SHAPES -> loadShapes(revisionId, zip, def)
                                GtfsFileDef.Kind.GEOJSON -> loadGeoJson(revisionId, zip)
                            }
                        metrics.gtfsRowsWritten(feed.code, def.entityType, rowCounts.getValue(def.entityType))
                        // Safety net: the reader and the analyzer must agree a required file has rows.
                        val analyzed = loadReport.analyzedRows(def.fileName)
                        if (def.required && rowCounts.getValue(def.entityType) == 0L && analyzed > 0) {
                            throw ParseException("${def.fileName}: analyzer read $analyzed row(s) but none could be loaded")
                        }
                    }
                    GtfsFileRegistry.defs
                        .map { it.fileName }
                        .filter { it in zip.fileNames && it !in loadReport.unavailableFiles }
                }

            revisionService.transition(revisionId, GtfsRevisionStatus.PARSING) {
                it.filesPresent = filesPresent
                it.rowCounts = rowCounts
            }

            val derivationStartedAt = Instant.now()
            runCatching { derivationService.rederive(revisionId) }
                .onSuccess {
                    metrics
                        .gtfsStageFinished(
                            feed.code, TransitTrackMetrics.ImportStage.DERIVE, TransitTrackMetrics.Outcome.SUCCESS,
                            Duration
                                .between(derivationStartedAt, Instant.now()),
                        )
                }.onFailure {
                    metrics
                        .gtfsStageFinished(
                            feed.code, TransitTrackMetrics.ImportStage.DERIVE, TransitTrackMetrics.Outcome.FAILED,
                            Duration
                                .between(derivationStartedAt, Instant.now()),
                        )
                }.getOrThrow()

            log.info("Marking feed ${feed.code} READY")
            revisionService.transition(revisionId, GtfsRevisionStatus.READY)

            if (feed.autoActivate ?: props.ingest.autoActivate) {
                revisionService.activate(revisionId)
                revisionService.prune(feed.id!!, props.retention.keepRevisionsPerFeed)
            }
            feedService.markIngested(feed.id!!)
            eventPublisher.publishEvent(IngestionFinishedEvent(revisionId))
            metrics.gtfsImportFinished(feed.code, TransitTrackMetrics.ImportOutcome.READY, Duration.between(startedAt, Instant.now()))
        } catch (e: Exception) {
            log.warn("ingest failed for revision {}: {}", revisionId, e.message, e)
            // RevisionService.fail / RevisionWriter only know the gtfs_* tables. A failure
            // AFTER a successful post-processor (READY, activate, prune, markIngested) would
            // otherwise leave e.g. a full derived schedule model hanging off a FAILED revision.
            // Idempotent: a post-processor's own cleanup may already have wiped them.
            postProcessors.forEach { runCatching { it.onIngestionFailure(revisionId) } }
            eventPublisher.publishEvent(IngestionFailedEvent(revisionId))
            revisionService.fail(revisionId, e.message ?: e.javaClass.simpleName)
            metrics.gtfsImportFinished(feed.code, TransitTrackMetrics.ImportOutcome.FAILED, Duration.between(startedAt, Instant.now()))
        } finally {
            runCatching { tempDir.toFile().deleteRecursively() }
        }
    }

    private fun tempRoot(): Path =
        if (props.ingest.tempDir.isBlank()) {
            Path.of(System.getProperty("java.io.tmpdir"))
        } else {
            Path.of(props.ingest.tempDir).also { Files.createDirectories(it) }
        }

    private fun loadCsv(
        revisionId: Long,
        zip: GtfsZip,
        def: GtfsFileDef,
    ): Long {
        log.info("Loading CSV ${def.entityType} from file ${def.fileName}")
        return writeRows(revisionId, zip, def)
    }

    private fun loadShapes(
        revisionId: Long,
        zip: GtfsZip,
        def: GtfsFileDef,
    ): Long {
        log.info("Loading SHAPES ${def.entityType} from file ${def.fileName}")
        val perShape = LinkedHashMap<String, MutableList<ShapePoint>>()
        val total =
            writeRows(revisionId, zip, def) { entity ->
                val pt = entity as ShapePoint
                perShape.getOrPut(pt.shapeId) { mutableListOf() }.add(pt)
            }

        val aggregates =
            perShape.map { (shapeId, pts) ->
                val ordered = pts.sortedBy { it.shapePtSequence }
                Shape(revisionId, shapeId, ordered.size, polylineLengthMeters(ordered))
            }
        writer.write(aggregates)

        return total
    }

    /**
     * Streams [def]'s file out of the archive row by row and bulk-writes it in batches. A row missing
     * a value its table requires is logged and skipped; the analyzer has already reported it.
     */
    private fun writeRows(
        revisionId: Long,
        zip: GtfsZip,
        def: GtfsFileDef,
        onEntity: (RevisionScoped) -> Unit = {},
    ): Long {
        val batch = ArrayList<RevisionScoped>(props.ingest.batchSize)
        var total = 0L
        var skipped = 0L

        zip.open(def.fileName)!!.use { input ->
            GtfsCsv.forEachRecord(input, def.fileName) { record ->
                val entity =
                    try {
                        def.map(revisionId, record)
                    } catch (e: MissingRequiredFieldException) {
                        log.warn("skipping {}:{} — missing {}", e.fileName, e.line, e.column)
                        skipped++
                        return@forEachRecord
                    }
                onEntity(entity)
                batch += entity
                if (batch.size >= props.ingest.batchSize) {
                    writer.write(batch)
                    total += batch.size
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) {
            writer.write(batch)
            total += batch.size
        }
        if (skipped > 0) {
            log.warn("Skipped {} row(s) of {} missing required values", skipped, def.fileName)
        }

        return total
    }

    private fun loadGeoJson(
        revisionId: Long,
        zip: GtfsZip,
    ): Long {
        log.info("Loading geojson")
        val rows =
            zip
                .open("locations.geojson")
                ?.use { GtfsGeoJsonReader.read(it) }
                ?.map {
                    Location(revisionId, it.locationId, it.stopName, it.stopDesc, it.geometryJson)
                }

        rows?.let { writer.write(it) }
        return rows?.size?.toLong() ?: 0
    }

    /**
     * Polyline length in metres (haversine). `0.0` for <2 points; `null` if any point lacks a
     * coordinate.
     */
    private fun polylineLengthMeters(points: List<ShapePoint>): Double? {
        if (points.size < 2) return 0.0
        var meters = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val aLat = a.shapePtLat ?: return null
            val aLon = a.shapePtLon ?: return null
            val bLat = b.shapePtLat ?: return null
            val bLon = b.shapePtLon ?: return null
            meters += haversineDistance(aLat, aLon, bLat, bLon)
        }
        return meters
    }
}
