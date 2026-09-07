package eu.transittrack.gtfs.export

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import kotlin.io.path.name

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Reaps `gtfs-export-*.zip` spool files that [RevisionExportController] left in `java.io.tmpdir`
 * because a client aborted the download before the body finished streaming. On the happy path the
 * OS releases the file once the response stream closes and this only ever sees stragglers.
 * Mirrors [eu.transittrack.avl.ingest.AvlRetentionScheduler]'s cron style.
 */
@Component
class ExportTempFileSweeper(
    @Value("\${transittrack.export.temp-file-max-age-minutes:15}") private val maxAgeMinutes: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tmpDir: Path = Paths.get(System.getProperty("java.io.tmpdir"))

    @Scheduled(cron = "\${transittrack.export.temp-sweep-cron:0 */5 * * * *}")
    fun sweep() {
        val cutoff = Instant.now().minus(Duration.ofMinutes(maxAgeMinutes))
        var deleted = 0
        runCatching {
            Files.list(tmpDir).use { paths ->
                paths
                    .filter { it.name.startsWith("gtfs-export-") && it.name.endsWith(".zip") }
                    .filter { runCatching { Files.getLastModifiedTime(it).toInstant().isBefore(cutoff) }.getOrDefault(false) }
                    .forEach { if (runCatching { Files.deleteIfExists(it) }.getOrDefault(false)) deleted++ }
            }
        }.onFailure { log.warn("export temp sweep failed", it) }
        if (deleted > 0) log.info("export temp sweep: deleted {} stale spool file(s)", deleted)
    }
}
