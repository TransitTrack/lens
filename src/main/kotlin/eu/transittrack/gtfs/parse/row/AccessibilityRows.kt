package eu.transittrack.gtfs.parse.row

import eu.transittrack.gtfs.parse.GtfsRecord

data class PathwayRow(
    val line: Long,
    val pathwayId: String?,
    val fromStopId: String?,
    val toStopId: String?,
    val pathwayMode: Int?,
    val isBidirectional: Int?,
    val length: Double?,
    val traversalTime: Int?,
    val stairCount: Int?,
    val maxSlope: Double?,
    val minWidth: Double?,
    val signpostedAs: String?,
    val reversedSignpostedAs: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            PathwayRow(
                line = r.line,
                pathwayId = r.string("pathway_id"),
                fromStopId = r.string("from_stop_id"),
                toStopId = r.string("to_stop_id"),
                pathwayMode = r.int("pathway_mode"),
                isBidirectional = r.int("is_bidirectional"),
                length = r.double("length"),
                traversalTime = r.int("traversal_time"),
                stairCount = r.int("stair_count"),
                maxSlope = r.double("max_slope"),
                minWidth = r.double("min_width"),
                signpostedAs = r.string("signposted_as"),
                reversedSignpostedAs = r.string("reversed_signposted_as"),
            )
    }
}

data class LevelRow(
    val line: Long,
    val levelId: String?,
    val levelIndex: Double?,
    val levelName: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            LevelRow(
                line = r.line,
                levelId = r.string("level_id"),
                levelIndex = r.double("level_index"),
                levelName = r.string("level_name"),
            )
    }
}

data class LocationGroupRow(
    val line: Long,
    val locationGroupId: String?,
    val locationGroupName: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            LocationGroupRow(
                line = r.line,
                locationGroupId = r.string("location_group_id"),
                locationGroupName = r.string("location_group_name"),
            )
    }
}

data class LocationGroupStopRow(
    val line: Long,
    val locationGroupId: String?,
    val stopId: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            LocationGroupStopRow(
                line = r.line,
                locationGroupId = r.string("location_group_id"),
                stopId = r.string("stop_id"),
            )
    }
}

data class BookingRuleRow(
    val line: Long,
    val bookingRuleId: String?,
    val bookingType: Int?,
    val priorNoticeDurationMin: Int?,
    val priorNoticeDurationMax: Int?,
    val priorNoticeLastDay: Int?,
    val priorNoticeLastTime: Int?,
    val priorNoticeStartDay: Int?,
    val priorNoticeStartTime: Int?,
    val priorNoticeServiceId: String?,
    val message: String?,
    val pickupMessage: String?,
    val dropOffMessage: String?,
    val phoneNumber: String?,
    val infoUrl: String?,
    val bookingUrl: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            BookingRuleRow(
                line = r.line,
                bookingRuleId = r.string("booking_rule_id"),
                bookingType = r.int("booking_type"),
                priorNoticeDurationMin = r.int("prior_notice_duration_min"),
                priorNoticeDurationMax = r.int("prior_notice_duration_max"),
                priorNoticeLastDay = r.int("prior_notice_last_day"),
                priorNoticeLastTime = r.time("prior_notice_last_time"),
                priorNoticeStartDay = r.int("prior_notice_start_day"),
                priorNoticeStartTime = r.time("prior_notice_start_time"),
                priorNoticeServiceId = r.string("prior_notice_service_id"),
                message = r.string("message"),
                pickupMessage = r.string("pickup_message"),
                dropOffMessage = r.string("drop_off_message"),
                phoneNumber = r.string("phone_number"),
                infoUrl = r.string("info_url"),
                bookingUrl = r.string("booking_url"),
            )
    }
}
