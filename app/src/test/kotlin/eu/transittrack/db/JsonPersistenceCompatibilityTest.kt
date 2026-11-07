package eu.transittrack.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

import eu.transittrack.ProjectJson
import eu.transittrack.avl.model.AvlFeedTable
import eu.transittrack.gtfs.export.ValidationNotice
import eu.transittrack.gtfs.export.ValidationResult
import eu.transittrack.gtfs.revision.GtfsRevisionTable
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.schedule.model.BlockTable

class JsonPersistenceCompatibilityTest {
    @Test
    fun `typed jsonb codecs read existing payloads and keep enum names and long precision`() {
        assertEquals(
            listOf(PredictionAlgorithm.KALMAN, PredictionAlgorithm.SCHEDULE_ADHERENCE),
            AvlFeedTable.predictionAlgorithms.columnType.valueFromDB("""["KALMAN","SCHEDULE_ADHERENCE"]"""),
        )
        assertEquals(
            """["KALMAN","HISTORICAL_AVERAGE"]""",
            AvlFeedTable.predictionAlgorithms.columnType.notNullValueToDB(
                listOf(PredictionAlgorithm.KALMAN, PredictionAlgorithm.HISTORICAL_AVERAGE),
            ),
        )
        assertEquals(
            mapOf("Authorization" to "quoted \"value\"", "Unicode" to "\u00e9"),
            AvlFeedTable.headers.columnType.valueFromDB("""{"Authorization":"quoted \"value\"","Unicode":"\u00e9"}"""),
        )
        assertEquals(
            mapOf("stops" to Long.MAX_VALUE),
            GtfsRevisionTable.rowCounts.columnType.valueFromDB("""{"stops":9223372036854775807}"""),
        )
        assertEquals(
            """{"stops":9223372036854775807}""",
            GtfsRevisionTable.rowCounts.columnType.notNullValueToDB(mapOf("stops" to Long.MAX_VALUE)),
        )
        assertEquals(emptyList(), GtfsRevisionTable.filesPresent.columnType.valueFromDB("[]"))
        assertEquals(listOf("r1", "r2"), BlockTable.routeIds.columnType.valueFromDB("""["r1","r2"]"""))
    }

    @Test
    fun `raw jsonb payloads are not double encoded`() {
        val payload = """{"coordinates":[[17.0,51.0]],"optional":null}"""
        assertEquals(payload, GtfsRevisionTable.validationReport.columnType.notNullValueToDB(payload))
        assertEquals(payload, GtfsRevisionTable.lastValidation.columnType.valueFromDB(payload))
    }

    @Test
    fun `invalid stored enum names fail explicitly`() {
        assertFailsWith<IllegalArgumentException> {
            AvlFeedTable.predictionAlgorithms.columnType.valueFromDB("""["UNKNOWN"]""")
        }
        assertFailsWith<SerializationException> {
            GtfsRevisionTable.rowCounts.columnType.valueFromDB("""{"stops":"not-a-number"}""")
        }
    }

    @Test
    fun `validation summaries keep legacy shape and shared json retains explicit nulls`() {
        val summary = ValidationResult(1, 0, listOf(ValidationNotice("ERROR", "RULE", "message")), false)
        assertEquals(
            ProjectJson.parseToJsonElement(
                """{"errorCount":1,"warningCount":0,"notices":[{"severity":"ERROR","code":"RULE","message":"message"}],"publishable":false}""",
            ),
            ProjectJson.parseToJsonElement(ProjectJson.encodeToString(summary)),
        )
        assertEquals(
            JsonNull,
            ProjectJson.encodeToJsonElement(mapOf("missing" to null, "value" to "text")).jsonObject["missing"],
        )
        assertEquals(JsonPrimitive("text"), ProjectJson.encodeToJsonElement("text"))
    }
}
