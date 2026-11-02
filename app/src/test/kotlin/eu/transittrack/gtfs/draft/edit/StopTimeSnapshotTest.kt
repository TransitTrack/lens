package eu.transittrack.gtfs.draft.edit

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

import eu.transittrack.ProjectJson
import eu.transittrack.gtfs.model.StopTime

class StopTimeSnapshotTest {
    @Test
    fun `snapshot retains explicit nulls and native numeric values in the stored shape`() {
        val row = StopTime(
            revisionId = 1,
            tripId = "trip",
            stopSequence = 7,
            stopId = null,
            arrivalTime = null,
            departureTime = 0,
            locationGroupId = null,
            locationId = null,
            stopHeadsign = "0",
            startPickupDropOffWindow = null,
            endPickupDropOffWindow = null,
            pickupType = 0,
            dropOffType = null,
            continuousPickup = null,
            continuousDropOff = null,
            shapeDistTraveled = 1.25,
            timepoint = null,
            pickupBookingRuleId = null,
            dropOffBookingRuleId = null,
        )
        val legacy = ProjectJson.parseToJsonElement(
            """
            {
              "tripId":"trip","stopSequence":7,"stopId":null,"arrivalTime":null,"departureTime":0,
              "locationGroupId":null,"locationId":null,"stopHeadsign":"0",
              "startPickupDropOffWindow":null,"endPickupDropOffWindow":null,"pickupType":0,
              "dropOffType":null,"continuousPickup":null,"continuousDropOff":null,
              "shapeDistTraveled":1.25,"timepoint":null,"pickupBookingRuleId":null,"dropOffBookingRuleId":null
            }
            """.trimIndent(),
        )
        assertThat(StopTimeSnapshot.toNode(row)).isEqualTo(legacy)
    }
}
