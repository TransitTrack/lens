@file:Suppress("ktlint:standard:filename")

package eu.transittrack.gtfs.draft.edit

import java.time.LocalDate

import tools.jackson.databind.node.ObjectNode

import eu.transittrack.gtfs.model.Calendar

private fun ObjectNode.putDate(
    name: String,
    v: LocalDate?,
): ObjectNode = if (v == null) putNull(name) else put(name, v.toString())

private fun dateOrNull(node: tools.jackson.databind.JsonNode): LocalDate? = if (node.isNull) null else LocalDate.parse(node.asString())

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
        val existing = ctx.calendars.findByServiceId(ctx.revisionId, serviceId)
        val fwd =
            ctx.json
                .createObjectNode()
                .put("serviceId", serviceId)
                .put("existed", true)
                .put("monday", monday)
                .put("tuesday", tuesday)
                .put("wednesday", wednesday)
                .put("thursday", thursday)
                .put("friday", friday)
                .put("saturday", saturday)
                .put("sunday", sunday)
                .putDate("startDate", startDate)
                .putDate("endDate", endDate)
        val inv =
            if (existing == null) {
                ctx.json
                    .createObjectNode()
                    .put("serviceId", serviceId)
                    .put("existed", false)
            } else {
                ctx.json
                    .createObjectNode()
                    .put("serviceId", serviceId)
                    .put("existed", true)
                    .put("monday", existing.monday ?: false)
                    .put("tuesday", existing.tuesday ?: false)
                    .put("wednesday", existing.wednesday ?: false)
                    .put("thursday", existing.thursday ?: false)
                    .put("friday", existing.friday ?: false)
                    .put("saturday", existing.saturday ?: false)
                    .put("sunday", existing.sunday ?: false)
                    .putDate("startDate", existing.startDate)
                    .putDate("endDate", existing.endDate)
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
            direction: tools.jackson.databind.JsonNode,
        ) {
            val serviceId = direction.get("serviceId").asString()
            if (!direction.get("existed").asBoolean()) {
                ctx.calendars.findByServiceId(ctx.revisionId, serviceId)?.let { ctx.calendars.delete(it) }
                return
            }
            val row = ctx.calendars.findByServiceId(ctx.revisionId, serviceId) ?: Calendar(
                revisionId = ctx.revisionId,
                serviceId = serviceId,
                monday = null, tuesday = null, wednesday = null, thursday = null,
                friday = null, saturday = null, sunday = null,
                startDate = null, endDate = null,
            )
            row.monday = direction.get("monday").asBoolean()
            row.tuesday = direction.get("tuesday").asBoolean()
            row.wednesday = direction.get("wednesday").asBoolean()
            row.thursday = direction.get("thursday").asBoolean()
            row.friday = direction.get("friday").asBoolean()
            row.saturday = direction.get("saturday").asBoolean()
            row.sunday = direction.get("sunday").asBoolean()
            row.startDate = dateOrNull(direction.get("startDate"))
            row.endDate = dateOrNull(direction.get("endDate"))
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
        val existing = ctx.calendarDates.findByServiceId(ctx.revisionId, serviceId).find { it.date == date }
        val fwd =
            ctx.json
                .createObjectNode()
                .put("serviceId", serviceId)
                .put("date", date.toString())
                .let { if (exceptionType == null) it.putNull("exceptionType") else it.put("exceptionType", exceptionType) }
        val inv =
            ctx.json
                .createObjectNode()
                .put("serviceId", serviceId)
                .put("date", date.toString())
                .let {
                    val old = existing?.exceptionType
                    if (old == null) it.putNull("exceptionType") else it.put("exceptionType", old)
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
            direction: tools.jackson.databind.JsonNode,
        ) {
            val serviceId = direction.get("serviceId").asString()
            val date = LocalDate.parse(direction.get("date").asString())
            val existing = ctx.calendarDates.findByServiceId(ctx.revisionId, serviceId).find { it.date == date }
            val typeNode = direction.get("exceptionType")
            if (typeNode.isNull) {
                existing?.let { ctx.calendarDates.delete(it) }
                return
            }
            val row = existing ?: eu.transittrack.gtfs.model.CalendarDate(
                revisionId = ctx.revisionId,
                serviceId = serviceId,
                date = date,
                exceptionType = null,
            )
            row.exceptionType = typeNode.asInt()
            ctx.calendarDates.save(row)
        }

        init {
            EditOpRegistry.register("SET_CALENDAR_EXCEPTION") { ctx, dir -> { apply(ctx, dir) } }
        }
    }
}
