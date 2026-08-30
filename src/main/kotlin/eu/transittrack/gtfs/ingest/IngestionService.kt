package eu.transittrack.gtfs.ingest

import eu.transittrack.gtfs.config.GtfsProperties
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.model.GtfsLocation
import eu.transittrack.gtfs.model.GtfsShape
import eu.transittrack.gtfs.model.GtfsShapePoint
import eu.transittrack.gtfs.model.GtfsShapeRepository
import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.parse.GtfsCsvReader
import eu.transittrack.gtfs.parse.GtfsFileDef
import eu.transittrack.gtfs.parse.GtfsFileRegistry
import eu.transittrack.gtfs.parse.GtfsGeoJsonReader
import eu.transittrack.gtfs.parse.GtfsParseException
import eu.transittrack.gtfs.parse.mapper.mapShapePoint
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.validate.GtfsValidationException
import eu.transittrack.gtfs.validate.GtfsValidator
import eu.transittrack.haversineMeters
import eu.transittrack.toRadians
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Orchestrates the GTFS ingestion pipeline: download -> unchanged-check -> extract ->
 * required-file check -> parse & bulk-write in registry order -> shape aggregates ->
 * derive dates -> READY -> (auto-)activate -> prune.
 *
 * The pipeline runs synchronously here; Task 20 adds the async wrapper and an
 * `ingestBlocking` split. Every failure is funnelled to [RevisionService.fail] and the
 * scratch directory is always deleted.
 */
@Service
class IngestionService(
    private val downloader: FeedDownloader,
    private val revisionService: RevisionService,
    private val writer: RevisionWriter,
    private val feeds: GtfsFeedRepository,
    private val feedService: GtfsFeedService,
    private val props: GtfsProperties,
    private val shapes: GtfsShapeRepository,
    private val validator: GtfsValidator,
    private val gtfsIngestExecutor: org.springframework.core.task.TaskExecutor,
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
        val feed = feeds.findById(revisionService.revision(revisionId).feedId).orElseThrow()
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

            log.info("Starting processing of ${feed.code}")
            revisionService.transition(revisionId, GtfsRevisionStatus.PARSING) {
                it.contentSha256 = dl.sha256
                it.byteSize = dl.byteSize
            }

            val present = GtfsArchive.extract(zipPath, tempDir).toSet()
            requirePresent(present)

            val rowCounts = LinkedHashMap<String, Long>()
            for (def in GtfsFileRegistry.orderedForParsing) {
                if (def.fileName !in present) continue
                rowCounts[def.entityType] = when (def.kind) {
                    GtfsFileDef.Kind.CSV -> loadCsv(revisionId, tempDir, def)
                    GtfsFileDef.Kind.SHAPES -> loadShapes(revisionId, tempDir)
                    GtfsFileDef.Kind.GEOJSON -> loadGeoJson(revisionId, tempDir)
                }
            }

            log.info("Starting validation of ${feed.code}")
            revisionService.transition(revisionId, GtfsRevisionStatus.VALIDATING) {
                it.filesPresent = present.sorted()
                it.rowCounts = rowCounts
            }

            val report = validator.validate(revisionId)
            revisionService.transition(revisionId, GtfsRevisionStatus.VALIDATING) {
                it.validationReport = report.toJson()
            }
            if (props.ingest.strictValidation && report.errorCount > 0) {
                throw GtfsValidationException(report)
            }

            revisionService.deriveDates(revisionId)
            log.info("Marking feed ${feed.code} READY")
            revisionService.transition(revisionId, GtfsRevisionStatus.READY)

            if (feed.autoActivate ?: props.ingest.autoActivate) {
                revisionService.activate(revisionId)
                revisionService.prune(feed.id!!, props.retention.keepRevisionsPerFeed)
            }
            feedService.markIngested(feed.id!!)
        } catch (e: Exception) {
            log.warn("ingest failed for revision {}: {}", revisionId, e.message)
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

    private fun loadCsv(revisionId: Long, dir: Path, def: GtfsFileDef): Long {
        log.info("Loading CSV ${def.entityType} file ${def.fileName}")
        val batch = ArrayList<RevisionScoped>(props.ingest.batchSize)
        var total = 0L
        GtfsArchive.openFile(dir, def.fileName)!!.use { input ->
            GtfsCsvReader.read(input) { row -> def.map(revisionId, row) }.forEach { entity ->
                batch += entity
                if (batch.size >= props.ingest.batchSize) {
                    writer.write(batch); total += batch.size; batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) { writer.write(batch); total += batch.size }
        return total
    }

    private fun loadShapes(revisionId: Long, dir: Path): Long {
        log.info("Loading shapes")
        val batch = ArrayList<RevisionScoped>(props.ingest.batchSize)
        val perShape = LinkedHashMap<String, MutableList<GtfsShapePoint>>()
        var total = 0L
        GtfsArchive.openFile(dir, "shapes.txt")!!.use { input ->
            GtfsCsvReader.read(input) { row -> mapShapePoint(revisionId, row) }.forEach { pt ->
                batch += pt
                perShape.getOrPut(pt.shapeId) { mutableListOf() }.add(pt)
                if (batch.size >= props.ingest.batchSize) {
                    writer.write(batch); total += batch.size; batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) { writer.write(batch); total += batch.size }

        val aggregates = perShape.map { (shapeId, pts) ->
            val ordered = pts.sortedBy { it.shapePtSequence }
            GtfsShape(revisionId, shapeId, ordered.size, polylineLengthMeters(ordered))
        }
        writer.write(aggregates)
        return total
    }

    private fun loadGeoJson(revisionId: Long, dir: Path): Long {
        log.info("Loading geojson")
        val rows = GtfsArchive.openFile(dir, "locations.geojson")!!.use { GtfsGeoJsonReader.read(it) }
            .map { GtfsLocation(revisionId, it.locationId, it.stopName, it.stopDesc, it.geometryJson) }
        writer.write(rows)
        return rows.size.toLong()
    }

    /** Polyline length in metres (haversine). `0.0` for <2 points; `null` if any point lacks a coordinate. */
    private fun polylineLengthMeters(points: List<GtfsShapePoint>): Double? {
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
