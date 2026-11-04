@file:Suppress("ktlint:standard:filename")

package eu.transittrack.gtfs.draft.edit

import java.time.LocalDate

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import eu.transittrack.gtfs.model.Calendar

private fun dateOrNull(node: JsonElement): LocalDate? = if (node == JsonNull) null else LocalDate.parse(node.jsonPrimitive.content)

private fun JsonElement.nullableBoolean(): Boolean? = if (this == JsonNull) null else jsonPrimitive.boolean

/**
 * Full-replace upsert of a `calendars` row for [serviceId]: creates it if absent, otherwise
 * overwrites every field (GTFS `calendar.txt` columns are all required, so there is no partial-
 * update case). The inverse either deletes the row it created or restores every prior field.
 */
class SetCalendarOp(
    private val serviceId: String,
    private val monday: Boolean,
    private val tuesday: Boolean,
    private val wednesday: Boolean,
    private val thursday: Boolean,
    private val friday: Boolean,
    private val saturday: Boolean,
    private val sunday: Boolean,
    private val startDate: LocalDate,
    private val endDate: LocalDate,
) : EditOp {
    override val op = "SET_CALENDAR"

    override fun plan(ctx: EditContext): PlannedEdit {
        require(!startDate.isAfter(endDate)) {
            "startDate ($startDate) must not be after endDate ($endDate)"
        }
        val existing = ctx.calendars.findByServiceId(ctx.revisionId, serviceId)
        val fwd =
            buildJsonObject {
                put("serviceId", serviceId)
                put("existed", true)
                put("monday", monday)
                put("tuesday", tuesday)
                put("wednesday", wednesday)
                put("thursday", thursday)
                put("friday", friday)
                put("saturday", saturday)
                put("sunday", sunday)
                put("startDate", startDate.toString())
                put("endDate", endDate.toString())
            }
        val inv =
            if (existing == null) {
                buildJsonObject {
                    put("serviceId", serviceId)
                    put("existed", false)
                }
            } else {
                buildJsonObject {
                    put("serviceId", serviceId)
                    put("existed", true)
                    put("monday", existing.monday)
                    put("tuesday", existing.tuesday)
                    put("wednesday", existing.wednesday)
                    put("thursday", existing.thursday)
                    put("friday", existing.friday)
                    put("saturday", existing.saturday)
                    put("sunday", existing.sunday)
                    put("startDate", existing.startDate?.toString())
                    put("endDate", existing.endDate?.toString())
                }
            }
        return PlannedEdit(
            "Set calendar for $serviceId",
            fwd,
            inv,
            mutate = { apply(ctx, fwd) },
        )
    }

    companion object {
        private fun apply(
            ctx: EditContext,
            direction: JsonElement,
        ) {
            val fields = direction.jsonObject
            val serviceId = fields.getValue("serviceId").jsonPrimitive.content
            if (!fields.getValue("existed").jsonPrimitive.boolean) {
                ctx.calendars.findByServiceId(ctx.revisionId, serviceId)?.let { ctx.calendars.delete(it) }
                return
            }
            val replacement = Calendar(
                revisionId = ctx.revisionId,
                serviceId = serviceId,
                monday = fields.getValue("monday").nullableBoolean(),
                tuesday = fields.getValue("tuesday").nullableBoolean(),
                wednesday = fields.getValue("wednesday").nullableBoolean(),
                thursday = fields.getValue("thursday").nullableBoolean(),
                friday = fields.getValue("friday").nullableBoolean(),
                saturday = fields.getValue("saturday").nullableBoolean(),
                sunday = fields.getValue("sunday").nullableBoolean(),
                startDate = dateOrNull(fields.getValue("startDate")),
                endDate = dateOrNull(fields.getValue("endDate")),
            )
            val row = ctx.calendars.findByServiceId(ctx.revisionId, serviceId) ?: replacement
            row.monday = replacement.monday
            row.tuesday = replacement.tuesday
            row.wednesday = replacement.wednesday
            row.thursday = replacement.thursday
            row.friday = replacement.friday
            row.saturday = replacement.saturday
            row.sunday = replacement.sunday
            row.startDate = replacement.startDate
            row.endDate = replacement.endDate
            ctx.calendars.save(row)
        }

        init {
            EditOpRegistry.register("SET_CALENDAR") { ctx, dir -> { apply(ctx, dir) } }
        }
    }
}

/**
 * Upsert or delete a single `calendar_dates` row for `(serviceId, date)`. `exceptionType = null`
 * deletes the row (a no-op if none exists); `1`/`2` upserts it. Forward/inverse carry the full
 * `(serviceId, date, exceptionType | null)` triple, matching [UpdateStopTimeOp]'s "explicit value
 * vs. null" semantics.
 */
class SetCalendarExceptionOp(
    private val serviceId: String,
    private val date: LocalDate,
    private val exceptionType: Int?,
) : EditOp {
    override val op = "SET_CALENDAR_EXCEPTION"

    override fun plan(ctx: EditContext): PlannedEdit {
        require(exceptionType == null || exceptionType == 1 || exceptionType == 2) {
            "exceptionType must be 1, 2, or null, got $exceptionType"
        }
        val existing = ctx.calendarDates.findByServiceId(ctx.revisionId, serviceId).find { it.date == date }
        val fwd =
            buildJsonObject {
                put("serviceId", serviceId)
                put("date", date.toString())
                put("exceptionType", exceptionType)
            }
        val inv =
            buildJsonObject {
                put("serviceId", serviceId)
                put("date", date.toString())
                put("exceptionType", existing?.exceptionType)
            }
        return PlannedEdit(
            "Set calendar exception $serviceId @ $date",
            fwd,
            inv,
            mutate = { apply(ctx, fwd) },
        )
    }

    companion object {
        private fun apply(
            ctx: EditContext,
            direction: JsonElement,
        ) {
            val fields = direction.jsonObject
            val serviceId = fields.getValue("serviceId").jsonPrimitive.content
            val date = LocalDate.parse(fields.getValue("date").jsonPrimitive.content)
            val existing = ctx.calendarDates.findByServiceId(ctx.revisionId, serviceId).find { it.date == date }
            val typeNode = fields.getValue("exceptionType")
            if (typeNode == JsonNull) {
                existing?.let { ctx.calendarDates.delete(it) }
                return
            }
            val row = existing ?: eu.transittrack.gtfs.model.CalendarDate(
                revisionId = ctx.revisionId,
                serviceId = serviceId,
                date = date,
                exceptionType = null,
            )
            row.exceptionType = typeNode.jsonPrimitive.int
            ctx.calendarDates.save(row)
        }

        init {
            EditOpRegistry.register("SET_CALENDAR_EXCEPTION") { ctx, dir -> { apply(ctx, dir) } }
        }
    }
}
