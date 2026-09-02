package eu.transittrack.gtfs.ingest

import eu.transittrack.gtfs.config.GtfsProperties
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.model.*
import eu.transittrack.gtfs.parse.GtfsFileDef
import eu.transittrack.gtfs.parse.GtfsFileRegistry
import eu.transittrack.gtfs.parse.GtfsGeoJsonReader
import eu.transittrack.gtfs.parse.GtfsParseException
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.validate.GtfsFeedLoader
import eu.transittrack.gtfs.validate.GtfsValidationException
import eu.transittrack.haversineMeters
import org.mobilitydata.gtfsvalidator.table.GtfsEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

/**
 * Orchestrates the GTFS ingestion pipeline: download -> unchanged-check ->
 * validate (MobilityData gtfs-validator on the archive; strict mode fails here,
 * before anything is written) -> extract -> required-file check -> parse &
 * bulk-write in registry order -> shape aggregates -> derive dates -> DERIVING
 * (the `eu.transittrack.schedule` model, unless `transittrack.schedule.enabled`
 * is false) -> READY -> (auto-)activate -> prune.
 *
 * [ingest] runs the pipeline on the ingest executor and returns immediately;
 * [ingestBlocking] runs it inline. Every failure is funnelled to [RevisionService.fail],
 * the revision's derived schedule rows are wiped, and the scratch directory is always
 * deleted.
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
    private val scheduleDerivation: eu.transittrack.schedule.derive.ScheduleDerivationService,
    private val scheduleProps: eu.transittrack.schedule.config.ScheduleProperties,
    private val scheduleWriter: eu.transittrack.schedule.derive.ScheduleWriter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Starts an ingest and returns the freshly created PENDING revision immediately; the
     * pipeline runs on [gtfsIngestExecutor]. Use [ingestBlocking] when the caller needs
     * the finished revision (tests, CLI).
     */
    fun ingest(feedCode: String): GtfsRevision {
        val revision = openRevision(feedCode)
        gtfsIngestExecutor.execute {
            runPipeline(revision.id!!)
        }
        return revision
    }

    /** Synchronous ingest: runs the pipeline inline and returns the finished revision. */
    fun ingestBlocking(feedCode: String): GtfsRevision {
        val revision = openRevision(feedCode)
        runPipeline(revision.id!!)
        return revisionService.revision(revision.id!!)
    }

    /** Feed lookup + enabled/in-progress guards + PENDING revision creation. */
    private fun openRevision(feedCode: String): GtfsRevision {
        val feed = feeds.findByCode(feedCode)
            ?: throw IllegalArgumentException("no feed '$feedCode'")
        require(feed.enabled) { "feed '$feedCode' is disabled" }
        check(!revisionService.hasInProgress(feed.id!!)) {
            "feed '$feedCode' already has an ingest in progress"
        }
        return revisionService.createPending(feed.id!!, feed.url)
    }

    fun runPipeline(revisionId: Long) {
        val feedId = revisionService.revision(revisionId).feedId
        val feed = feeds.findById(feedId).orElseThrow()
        val tempDir = createTempDirectory(tempRoot(), "gtfs-$revisionId-")
        val zipPath = tempDir.resolve("feed.zip")
        try {
            log.info("Starting download of ${feed.code}")
            revisionService.transition(revisionId, GtfsRevisionStatus.DOWNLOADING)
            val dl = downloader.download(feed.url, zipPath)

            val activeSha = revisionService.activeRevisionId(feed.id!!)
                ?.let { revisionService.revision(it).contentSha256 }
            if (activeSha != null && activeSha == dl.sha256) {
                revisionService.transition(revisionId, GtfsRevisionStatus.DOWNLOADING) {
                    it.contentSha256 = dl.sha256
                    it.byteSize = dl.byteSize
                }
                revisionService.markUnchanged(revisionId)
                feedService.markIngested(feed.id!!)
                log.info("Finished download and detected no changes for ${feed.code}")
                return
            }

            log.info("Starting validation of ${feed.code}")
            revisionService.transition(revisionId, GtfsRevisionStatus.VALIDATING) {
                it.contentSha256 = dl.sha256
                it.byteSize = dl.byteSize
            }

            val loadReport = feedLoader.load(zipPath)
            revisionService.transition(revisionId, GtfsRevisionStatus.VALIDATING) {
                it.validationReport = loadReport.toJson()
            }
            if (!loadReport.loaded || props.ingest.strictValidation && loadReport.errorCount > 0) {
                throw GtfsValidationException(loadReport)
            }

            log.info("Starting processing of ${feed.code}")
            revisionService.transition(revisionId, GtfsRevisionStatus.PARSING)

            val rowCounts = LinkedHashMap<String, Long>()
            for (def in GtfsFileRegistry.orderedForParsing) {
                if (!loadReport.hasFile(def.fileName))
                    continue

                val entities = loadReport.getFileContent(def.fileName)

                rowCounts[def.entityType] = when (def.kind) {
                    GtfsFileDef.Kind.CSV -> loadCsv(revisionId, entities, def)
                    GtfsFileDef.Kind.SHAPES -> loadShapes(revisionId, entities, def)
                    GtfsFileDef.Kind.GEOJSON -> loadGeoJson(revisionId, tempDir)
                }
            }

            revisionService.transition(revisionId, GtfsRevisionStatus.PARSING) {
                it.filesPresent = loadReport.filesPresent
                it.rowCounts = rowCounts
            }

            revisionService.deriveDates(revisionId)

            if (scheduleProps.enabled) {
                log.info("Deriving schedule model for ${feed.code}")
                revisionService.transition(revisionId, GtfsRevisionStatus.DERIVING)
                val derived = scheduleDerivation.derive(revisionId)
                revisionService.mergeRowCounts(revisionId, derived)
            }

            log.info("Marking feed ${feed.code} READY")
            revisionService.transition(revisionId, GtfsRevisionStatus.READY)

            if (feed.autoActivate ?: props.ingest.autoActivate) {
                revisionService.activate(revisionId)
                revisionService.prune(feed.id!!, props.retention.keepRevisionsPerFeed)
            }
            feedService.markIngested(feed.id!!)
        } catch (e: Exception) {
            log.warn("ingest failed for revision {}: {}", revisionId, e.message, e)
            // RevisionService.fail / RevisionWriter only know the gtfs_* tables. A failure
            // AFTER a successful derivation (READY, activate, prune, markIngested) would
            // otherwise leave a full derived model hanging off a FAILED revision.
            // Idempotent: derive()'s own cleanup may already have wiped them.
            runCatching { scheduleWriter.deleteForRevision(revisionId) }
            revisionService.fail(revisionId, e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { tempDir.toFile().deleteRecursively() }
        }
    }

    private fun requirePresent(present: Set<String>) {
        val missing = (GtfsFileRegistry.requiredFiles - present).toMutableList()
        if ("calendar.txt" !in present && "calendar_dates.txt" !in present) {
            missing += "calendar.txt or calendar_dates.txt"
        }
        if (missing.isNotEmpty()) {
            throw GtfsParseException("missing required file(s): ${missing.sorted().joinToString()}")
        }
    }

    private fun tempRoot(): Path =
        if (props.ingest.tempDir.isBlank()) {
            Path.of(System.getProperty("java.io.tmpdir"))
        } else {
            Path.of(props.ingest.tempDir).also { Files.createDirectories(it) }
        }

    private fun loadCsv(revisionId: Long, entities: List<GtfsEntity>, def: GtfsFileDef): Long {
        log.info("Loading CSV ${def.entityType} from file ${def.fileName}")
        val batch = ArrayList<RevisionScoped>(props.ingest.batchSize)
        var total = 0L

        entities.map { row ->
            def.map(revisionId, row)
        }.forEach { entity ->
            batch += entity
            if (batch.size >= props.ingest.batchSize) {
                writer.write(batch)
                total += batch.size
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            writer.write(batch); total += batch.size
        }

        return total
    }

    private fun loadShapes(revisionId: Long, entities: List<GtfsEntity>, def: GtfsFileDef): Long {
        log.info("Loading CSV ${def.entityType} from file ${def.fileName}")
        val batch = ArrayList<RevisionScoped>(props.ingest.batchSize)
        val perShape = LinkedHashMap<String, MutableList<ShapePoint>>()
        var total = 0L

        entities.map { row ->
            val pt = def.map(revisionId, row) as ShapePoint
            perShape.getOrPut(pt.shapeId) { mutableListOf() }.add(pt)
            pt
        }.forEach { entity ->
            batch += entity
            if (batch.size >= props.ingest.batchSize) {
                writer.write(batch)
                total += batch.size
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            writer.write(batch); total += batch.size
        }

        val aggregates = perShape.map { (shapeId, pts) ->
            val ordered = pts.sortedBy { it.shapePtSequence }
            Shape(revisionId, shapeId, ordered.size, polylineLengthMeters(ordered))
        }
        writer.write(aggregates)

        return total
    }

    private fun loadGeoJson(revisionId: Long, dir: Path): Long {
        log.info("Loading geojson")
        val rows = GtfsArchive.openFile(dir, "locations.geojson")
            ?.use { GtfsGeoJsonReader.read(it) }
            ?.map { Location(revisionId, it.locationId, it.stopName, it.stopDesc, it.geometryJson) }

        rows?.let { writer.write(it) }
        return rows?.size?.toLong() ?: 0
    }

    /** Polyline length in metres (haversine). `0.0` for <2 points; `null` if any point lacks a coordinate. */
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
            meters += haversineMeters(aLat, aLon, bLat, bLon)
        }
        return meters
    }

}
