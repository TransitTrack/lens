package eu.transittrack.gtfs.parse

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.serialization.json.Json

/** Expected values come from `value-formats.json`, captured from the MobilityData types we replace. */
class GtfsRecordTest {
    private val formats: Map<String, String> =
        Json.decodeFromString(
            BaselineSupport.baselineDir
                .resolve("value-formats.json")
                .toFile()
                .readText(),
        )

    private fun record(
        column: String,
        value: String,
    ): GtfsRecord {
        var out: GtfsRecord? = null
        GtfsCsv.forEachRecord("$column\n\"$value\"\n".byteInputStream(), "t.txt") { out = it }
        return out!!
    }

    @Test
    fun `string returns the trimmed value`() {
        assertThat(record("a", "  x ").string("a")).isEqualTo("x")
    }

    @Test
    fun `int parses or yields null`() {
        assertThat(record("a", "42").int("a")).isEqualTo(42)
        assertThat(record("a", "abc").int("a")).isNull()
    }

    @Test
    fun `double parses finite numbers only`() {
        assertThat(record("a", "51.5").double("a")).isEqualTo(51.5)
        assertThat(record("a", "NaN").double("a")).isNull()
        assertThat(record("a", "x").double("a")).isNull()
    }

    @Test
    fun `date parses yyyyMMdd only`() {
        assertThat(record("a", "20260131").date("a")).isEqualTo(LocalDate.of(2026, 1, 31))
        assertThat(record("a", "2026-01-01").date("a")).isNull()
        assertThat(record("a", "20261332").date("a")).isNull()
    }

    @Test
    fun `time converts to seconds since midnight including past 24h`() {
        assertThat(record("a", "25:01:30").time("a")).isEqualTo(formats.getValue("time:25:01:30").toInt())
        assertThat(record("a", "7:05:00").time("a")).isEqualTo(formats.getValue("time:7:05:00").toInt())
        assertThat(record("a", "25:61:00").time("a")).isNull()
        assertThat(record("a", "7:5:00").time("a")).isNull()
    }

    @Test
    fun `color matches the previous html format`() {
        assertThat(record("a", "ff00aa").color("a")).isEqualTo(formats.getValue("color:ff00aa"))
        assertThat(record("a", "FFFFFF").color("a")).isEqualTo(formats.getValue("color:FFFFFF"))
        assertThat(record("a", "red").color("a")).isNull()
        assertThat(record("a", "fff").color("a")).isNull()
    }

    @Test
    fun `language normalises to a BCP 47 tag`() {
        assertThat(record("a", "pt-br").language("a")).isEqualTo(formats.getValue("language:pt-br"))
        assertThat(record("a", "en").language("a")).isEqualTo(formats.getValue("language:en"))
        assertThat(record("a", "!!").language("a")).isNull()
    }

    @Test
    fun `timezone parses IANA zone ids`() {
        assertThat(record("a", "Europe/Bucharest").timezone("a")).isEqualTo(ZoneId.of("Europe/Bucharest"))
        assertThat(record("a", "Mars/Olympus").timezone("a")).isNull()
    }

    @Test
    fun `locale parses language tags`() {
        assertThat(record("a", "pt-br").locale("a")).isEqualTo(Locale.forLanguageTag("pt-BR"))
        assertThat(record("a", "!!").locale("a")).isNull()
    }

    @Test
    fun `currency accepts ISO 4217 codes only`() {
        assertThat(record("a", "EUR").currency("a")).isEqualTo(formats.getValue("currency:EUR"))
        assertThat(record("a", "XYZ").currency("a")).isNull()
    }

    @Test
    fun `absent column yields null for every accessor`() {
        val r = record("a", "1")
        assertThat(r.int("b")).isNull()
        assertThat(r.date("b")).isNull()
        assertThat(r.time("b")).isNull()
        assertThat(r.color("b")).isNull()
    }
}
