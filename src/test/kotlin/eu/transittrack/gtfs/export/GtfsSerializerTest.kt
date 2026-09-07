package eu.transittrack.gtfs.export

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import javax.sql.DataSource
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory

/**
 * `RevisionWriter` / the ingestion pipeline commit on their own connections, so this must not run
 * inside a rollback transaction — hence `NOT_SUPPORTED` plus explicit `@AfterEach` cleanup
 * (same wiring as `DraftServiceTest`).
 *
 * Note: `GtfsFileSchema.formatCell` casts DATE cells to `java.time.LocalDate`, but
 * `ResultSet.getObject` hands back `java.sql.Date` for DATE columns — `GtfsSerializer` converts
 * in its query lambda before calling `formatCell`.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GtfsSerializerTest(
    @Autowired val serializer: GtfsSerializer,
    @Autowired val stops: StopRepository,
    @Autowired val trips: TripRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val calendars: CalendarRepository,
    @Autowired val calendarDates: CalendarDateRepository,
    @Autowired val shapePoints: ShapePointRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val clean = mutableListOf<Long>()

    @AfterEach
    fun c() {
        clean.forEach {
            runCatching { writer.deleteAllForRevision(it) }
            runCatching { revisions.deleteById(it) }
        }
    }

    private fun entry(
        zipBytes: ByteArray,
        name: String,
    ): String? =
        ZipInputStream(zipBytes.inputStream()).use { zin ->
            generateSequence { zin.nextEntry }.forEach { e ->
                if (e.name == name) return zin.readBytes().toString(Charsets.UTF_8)
            }
            null
        }

    @Test
    fun `serialize then re-ingest yields the same core row counts and field values`() {
        val (_, base) = ingestFactory.ingest("schedule-sample")
        clean += base
        val baseStops = stops.findByRevisionId(base).size
        val baseTrips = trips.findByRevisionId(base).size
        val baseStopTimes = stopTimes.findByRevisionId(base).size
        val baseCalendars = calendars.findByRevisionId(base).size
        val baseCalendarDates = calendarDates.findByRevisionId(base).size
        val baseShapePoints = shapePoints.findByRevisionId(base).size
        assertThat(baseStopTimes).isGreaterThan(0)
        assertThat(baseCalendars).isGreaterThan(0)
        assertThat(baseShapePoints).isGreaterThan(0)

        val s1NameBase =
            jdbc.queryForObject(
                "select stop_name from stops where revision_id = ? and stop_id = 'S1'",
                String::class.java,
                base,
            )
        val wkStartBase =
            jdbc.queryForObject(
                "select start_date from calendars where revision_id = ? and service_id = 'WK'",
                java.sql.Date::class.java,
                base,
            )
        assertThat(s1NameBase).isNotNull()

        val zip = Files.createTempFile("export", ".zip")
        Files.newOutputStream(zip).use { serializer.serialize(base, it) }

        val reIngested = ingestFactory.ingestFromZip(zip)
        clean += reIngested

        assertThat(stops.findByRevisionId(reIngested).size).isEqualTo(baseStops)
        assertThat(trips.findByRevisionId(reIngested).size).isEqualTo(baseTrips)
        assertThat(stopTimes.findByRevisionId(reIngested).size).isEqualTo(baseStopTimes)
        assertThat(calendars.findByRevisionId(reIngested).size).isEqualTo(baseCalendars)
        assertThat(calendarDates.findByRevisionId(reIngested).size).isEqualTo(baseCalendarDates)
        assertThat(shapePoints.findByRevisionId(reIngested).size).isEqualTo(baseShapePoints)

        assertThat(
            jdbc.queryForObject(
                "select stop_name from stops where revision_id = ? and stop_id = 'S1'",
                String::class.java,
                reIngested,
            ),
        ).isEqualTo(s1NameBase)
        assertThat(
            jdbc.queryForObject(
                "select start_date from calendars where revision_id = ? and service_id = 'WK'",
                java.sql.Date::class.java,
                reIngested,
            ),
        ).isEqualTo(wkStartBase)
    }

    @Test
    fun `csv output has the exact GTFS header and RFC-4180 quotes special characters`() {
        val (_, base) = ingestFactory.ingest("schedule-sample")
        clean += base

        // S1 stop_name gets a comma AND a double-quote — must be emitted as "a, ""b""".
        jdbc.update(
            "update stops set stop_name = ? where revision_id = ? and stop_id = 'S1'",
            "a, \"b\"",
            base,
        )

        val bytes = ByteArrayOutputStream().also { serializer.serialize(base, it) }.toByteArray()
        val stopsCsv = entry(bytes, "stops.txt")
        assertThat(stopsCsv).isNotNull()
        val lines = stopsCsv!!.split("\r\n")

        assertThat(lines[0]).isEqualTo(
            "stop_id,stop_code,stop_name,tts_stop_name,stop_desc,stop_lat,stop_lon,zone_id,stop_url," +
                "location_type,parent_station,stop_timezone,wheelchair_boarding,level_id,platform_code",
        )
        val s1 = lines.single { it.startsWith("S1,") }
        val quotedCell = ",\"a, \"\"b\"\"\","
        assertThat(s1).contains(quotedCell)
    }
}
