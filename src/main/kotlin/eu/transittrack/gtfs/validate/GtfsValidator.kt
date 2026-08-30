package eu.transittrack.gtfs.validate

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Runs a fixed set of orphan-reference (dangling foreign key) checks over a single
 * loaded GTFS revision and produces a [ValidationReport].
 *
 * Each rule is a `count(*) + sample` query scoped to one `revision_id`; an issue is
 * emitted only when the orphan count is greater than zero. ERROR rules cover the
 * mandatory GTFS relationships (a trip must point at a real route/service, a
 * stop_time at a real trip/stop); WARNING rules cover optional relationships.
 */
@Component
class GtfsValidator(private val jdbc: JdbcTemplate) {

    private data class Rule(val name: String, val severity: Severity, val sql: String)

    // Each SQL returns (cnt bigint, sample text) for the bound revision id.
    private val rules = listOf(
        Rule(
            "trip.route_id->route", Severity.ERROR,
            """
            select count(*) cnt, coalesce(min(t.route_id), '') sample
            from gtfs_trip t
            where t.revision_id = ?
              and not exists (
                select 1 from gtfs_route r
                where r.revision_id = t.revision_id and r.route_id = t.route_id)
            """,
        ),
        Rule(
            "trip.service_id->calendar", Severity.ERROR,
            """
            select count(*) cnt, coalesce(min(t.service_id), '') sample
            from gtfs_trip t
            where t.revision_id = ?
              and not exists (
                select 1 from gtfs_calendar c
                where c.revision_id = t.revision_id and c.service_id = t.service_id)
              and not exists (
                select 1 from gtfs_calendar_date d
                where d.revision_id = t.revision_id and d.service_id = t.service_id)
            """,
        ),
        Rule(
            "stop_time.trip_id->trip", Severity.ERROR,
            """
            select count(*) cnt, coalesce(min(st.trip_id), '') sample
            from gtfs_stop_time st
            where st.revision_id = ?
              and not exists (
                select 1 from gtfs_trip t
                where t.revision_id = st.revision_id and t.trip_id = st.trip_id)
            """,
        ),
        Rule(
            "stop_time.stop_id->stop", Severity.ERROR,
            """
            select count(*) cnt, coalesce(min(st.stop_id), '') sample
            from gtfs_stop_time st
            where st.revision_id = ?
              and st.stop_id is not null
              and not exists (
                select 1 from gtfs_stop s
                where s.revision_id = st.revision_id and s.stop_id = st.stop_id)
            """,
        ),
        Rule(
            "trip.shape_id->shape", Severity.WARNING,
            """
            select count(*) cnt, coalesce(min(t.shape_id), '') sample
            from gtfs_trip t
            where t.revision_id = ?
              and t.shape_id is not null
              and not exists (
                select 1 from gtfs_shape s
                where s.revision_id = t.revision_id and s.shape_id = t.shape_id)
            """,
        ),
        Rule(
            "route.agency_id->agency", Severity.WARNING,
            """
            select count(*) cnt, coalesce(min(r.agency_id), '') sample
            from gtfs_route r
            where r.revision_id = ?
              and r.agency_id is not null
              and not exists (
                select 1 from gtfs_agency a
                where a.revision_id = r.revision_id and a.agency_id = r.agency_id)
            """,
        ),
        Rule(
            "stop.parent_station->stop", Severity.WARNING,
            """
            select count(*) cnt, coalesce(min(s.parent_station), '') sample
            from gtfs_stop s
            where s.revision_id = ?
              and s.parent_station is not null
              and not exists (
                select 1 from gtfs_stop p
                where p.revision_id = s.revision_id and p.stop_id = s.parent_station)
            """,
        ),
        Rule(
            "frequency.trip_id->trip", Severity.WARNING,
            """
            select count(*) cnt, coalesce(min(f.trip_id), '') sample
            from gtfs_frequency f
            where f.revision_id = ?
              and not exists (
                select 1 from gtfs_trip t
                where t.revision_id = f.revision_id and t.trip_id = f.trip_id)
            """,
        ),
        Rule(
            "transfer.stop_id->stop", Severity.WARNING,
            """
            select count(*) cnt, coalesce(min(coalesce(x.from_stop_id, x.to_stop_id)), '') sample
            from gtfs_transfer x
            where x.revision_id = ?
              and (
                (x.from_stop_id is not null and not exists (
                  select 1 from gtfs_stop s
                  where s.revision_id = x.revision_id and s.stop_id = x.from_stop_id))
                or (x.to_stop_id is not null and not exists (
                  select 1 from gtfs_stop s
                  where s.revision_id = x.revision_id and s.stop_id = x.to_stop_id)))
            """,
        ),
    )

    fun validate(revisionId: Long): ValidationReport {
        val issues = rules.mapNotNull { rule ->
            val row = jdbc.queryForMap(rule.sql, revisionId)
            val count = (row["cnt"] as Number).toLong()
            if (count == 0L) {
                null
            } else {
                ValidationIssue(rule.name, rule.severity, count, (row["sample"] as? String).orEmpty())
            }
        }
        return ValidationReport(issues)
    }
}
