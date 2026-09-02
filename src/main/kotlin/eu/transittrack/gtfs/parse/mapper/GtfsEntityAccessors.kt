package eu.transittrack.gtfs.parse.mapper

import java.time.LocalDate
import java.util.Currency
import java.util.Locale

import org.mobilitydata.gtfsvalidator.type.GtfsColor
import org.mobilitydata.gtfsvalidator.type.GtfsDate
import org.mobilitydata.gtfsvalidator.type.GtfsTime

/**
 * Conversions from the MobilityData gtfs-validator value types to the plain types the project's JPA
 * entities expect.
 *
 * All receivers are nullable so a mapper can write `r.startDate().takeIf { r.hasStartDate()
 * }.toLocalDate()` and get `null` whenever the source column was absent.
 */
fun GtfsColor?.toHex(): String? = this?.toHtmlColor()

fun GtfsDate?.toLocalDate(): LocalDate? = this?.localDate

fun GtfsTime?.toSeconds(): Int? = this?.secondsSinceMidnight

fun Locale?.toLanguageTagOrNull(): String? = this?.toLanguageTag()

fun Currency?.codeOrNull(): String? = this?.currencyCode
