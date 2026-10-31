package eu.transittrack.gtfs.read

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
class GtfsRecordsServiceTest(
    @Autowired val service: GtfsRecordsService,
    @Autowired val jdbc: JdbcTemplate,
) : PostgresPerMethodTest() {
    private fun literal(dataType: String): String =
        when (dataType) {
            "text", "character varying", "character" -> "'x'"
            "integer", "smallint", "bigint" -> "1"
            "double precision", "real", "numeric" -> "1.5"
            "boolean" -> "true"
            "date" -> "date '2026-01-02'"
            "timestamp without time zone", "timestamp with time zone" -> "now()"
            "jsonb" -> "'{}'::jsonb"
            "json" -> "'{}'::json"
            else -> error("unmapped column type $dataType")
        }

    /** Inserts one fully populated row into [table] for [revisionId] and returns its column names. */
    private fun seedRow(
        table: String,
        revisionId: Long,
    ) {
        val cols =
            jdbc.queryForList(
                """
                select column_name, data_type from information_schema.columns
                where table_schema = current_schema() and table_name = ? and column_name not in ('id', 'revision_id')
                order by ordinal_position
                """.trimIndent(),
                table,
            )
        val names = cols.map { it["column_name"] as String }
        val values = cols.map { literal(it["data_type"] as String) }
        jdbc.update(
            "insert into $table (id, revision_id${names.joinToString("") { ", $it" }}) " +
                "values (nextval('gtfs_entity_seq'), $revisionId${values.joinToString("") { ", $it" }})",
        )
    }

    @Test
    fun valueTypesUnchanged() {
        jdbc.update(
            "insert into gtfs_feed (id, code, name, url, enabled, source, created_at, updated_at) " +
                "values (1, 'g', 'G', 'x', true, 'CONFIG', now(), now())",
        )
        jdbc.update("insert into gtfs_revision (id, feed_id, status, source_url, created_at) values (1, 1, 'ACTIVE', 'x', now())")

        val actual = sortedMapOf<String, String>()
        for ((enum, table) in GtfsRecordsService.TABLE_BY_ENUM) {
            seedRow(table, 1)
            val rows = service.records("g", enum, "1")
            assertThat(rows.size).isEqualTo(1)
            actual[table] =
                rows[0]
                    .entries
                    .joinToString(", ") { "${it.key}=${it.value?.javaClass?.name?.removePrefix("java.lang.")}" }
        }
        val rendered = actual.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        assertThat(rendered).isEqualTo(EXPECTED)
    }

    private companion object {
        /** Simple class names (java.lang.* and PGobject) per column, in `select *` order, captured on the pre-Exposed JdbcTemplate code. */
        val EXPECTED =
            """
            areas: id=Long, revision_id=Long, area_id=String, area_name=String
            attributions: id=Long, revision_id=Long, attribution_id=String, agency_id=String, route_id=String, trip_id=String, organization_name=String, is_producer=Integer, is_operator=Integer, is_authority=Integer, attribution_url=String, attribution_email=String, attribution_phone=String
            booking_rules: id=Long, revision_id=Long, booking_rule_id=String, booking_type=Integer, prior_notice_duration_min=Integer, prior_notice_duration_max=Integer, prior_notice_last_day=Integer, prior_notice_last_time=Integer, prior_notice_start_day=Integer, prior_notice_start_time=Integer, prior_notice_service_id=String, message=String, pickup_message=String, drop_off_message=String, phone_number=String, info_url=String, booking_url=String
            fare_attributes: id=Long, revision_id=Long, fare_id=String, price=Double, currency_type=String, payment_method=Integer, transfers=Integer, agency_id=String, transfer_duration=Integer
            fare_leg_join_rules: id=Long, revision_id=Long, from_network_id=String, to_network_id=String, from_stop_id=String, to_stop_id=String
            fare_leg_rules: id=Long, revision_id=Long, leg_group_id=String, network_id=String, from_area_id=String, to_area_id=String, from_timeframe_group_id=String, to_timeframe_group_id=String, fare_product_id=String, rule_priority=Integer
            fare_media: id=Long, revision_id=Long, fare_media_id=String, fare_media_name=String, fare_media_type=Integer
            fare_products: id=Long, revision_id=Long, fare_product_id=String, fare_product_name=String, rider_category_id=String, fare_media_id=String, amount=Double, currency=String
            fare_rules: id=Long, revision_id=Long, fare_id=String, route_id=String, origin_id=String, destination_id=String, contains_id=String
            fare_transfer_rules: id=Long, revision_id=Long, from_leg_group_id=String, to_leg_group_id=String, transfer_count=Integer, duration_limit=Integer, duration_limit_type=Integer, fare_transfer_type=Integer, fare_product_id=String
            location_group_stops: id=Long, revision_id=Long, location_group_id=String, stop_id=String
            location_groups: id=Long, revision_id=Long, location_group_id=String, location_group_name=String
            locations: id=Long, revision_id=Long, location_id=String, stop_name=String, stop_desc=String, geometry=org.postgresql.util.PGobject
            networks: id=Long, revision_id=Long, network_id=String, network_name=String
            rider_categories: id=Long, revision_id=Long, rider_category_id=String, rider_category_name=String, is_default_fare_category=Integer, eligibility_url=String
            route_networks: id=Long, revision_id=Long, network_id=String, route_id=String
            stop_areas: id=Long, revision_id=Long, area_id=String, stop_id=String
            timeframes: id=Long, revision_id=Long, timeframe_group_id=String, start_time=Integer, end_time=Integer, service_id=String
            translations: id=Long, revision_id=Long, table_name=String, field_name=String, language=String, translation=String, record_id=String, record_sub_id=String, field_value=String
            """.trimIndent()
    }
}
