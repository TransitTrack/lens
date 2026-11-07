package eu.transittrack.avl.api

import java.util.Base64

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.avl.read.MapVehicle
import eu.transittrack.avl.read.MapVehicleReadService
import eu.transittrack.schedule.read.FeedGeometryService

@RestController
@RequestMapping("/api/feeds")
@ConditionalOnRole(Role.API)
class MapRestController(
    private val vehicles: MapVehicleReadService,
    private val geometry: FeedGeometryService,
) {
    @GetMapping("/{feedCode}/geometry")
    fun geometry(
        @PathVariable feedCode: String,
    ): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/geo+json")
            .header(HttpHeaders.CONTENT_ENCODING, "gzip")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .body(Base64.getDecoder().decode(geometry.feedGeometry(feedCode, null)))

    @GetMapping("/{feedCode}/vehicles")
    fun vehicles(
        @PathVariable feedCode: String,
        @RequestParam(defaultValue = "false") matchedOnly: Boolean,
    ): List<MapVehicle> = vehicles.vehicles(feedCode, matchedOnly)
}
