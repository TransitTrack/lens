package eu.transittrack.gtfs.parse

import eu.transittrack.gtfs.model.RevisionScoped
import org.mobilitydata.gtfsvalidator.table.GtfsEntity

class GtfsFileDef(
    val fileName: String,
    val entityType: String,
    val required: Boolean,
    val order: Int,
    val kind: Kind,
    val map: (revisionId: Long, row: GtfsEntity) -> RevisionScoped,
) {
    enum class Kind { CSV, GEOJSON, SHAPES }
}
