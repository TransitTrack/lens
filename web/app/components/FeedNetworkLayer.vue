<script setup lang="ts">
/**
 * GTFS route + stop base layer for a MapLibre map. Mount as a child of
 * <MglMap>. Renders route pattern polylines (coloured by route_color) and a
 * faint stop-dot layer; tune prominence with `dim` when it sits under live
 * markers.
 */
import type { CircleLayerSpecification, LineLayerSpecification } from 'maplibre-gl'

const props = withDefaults(
  defineProps<{
    lines: GeoJSON.FeatureCollection
    stops: GeoJSON.FeatureCollection
    /** softer styling for use as an underlay beneath vehicle markers */
    dim?: boolean
    /** hide the route lines while keeping the network data mounted */
    showRoutes?: boolean
    /** hide the stop dots (they can be noisy on a fleet map) */
    showStops?: boolean
    /** id of an existing layer to insert these below (e.g. a live marker layer
     * that must stay on top regardless of mount order) */
    before?: string
  }>(),
  { dim: false, showRoutes: true, showStops: true, before: undefined },
)

const colorMode = useColorMode()

const baseWidth = computed(() => (props.dim ? 1.5 : 2))
const baseOpacity = computed(() => (props.dim ? 0.72 : 0.9))

const linePaint = computed<LineLayerSpecification['paint']>(() => ({
  'line-color': ['get', 'color'],
  'line-width': baseWidth.value,
  'line-opacity': baseOpacity.value,
}))

/** A thin theme-aware casing keeps normalized GTFS route colors legible over
 * either basemap without changing the route color itself. */
const casingPaint = computed<LineLayerSpecification['paint']>(() => ({
  'line-color': colorMode.value === 'dark' ? '#0b0d0e' : '#f8fafc',
  'line-width': baseWidth.value + 1,
  'line-opacity': props.dim ? 0.22 : 0.3,
}))

const stopPaint = computed<CircleLayerSpecification['paint']>(() => ({
  'circle-radius': props.dim ? 2 : 3,
  'circle-color': '#64748b',
  'circle-opacity': props.dim ? 0.45 : 0.8,
  'circle-stroke-width': props.dim ? 0 : 1,
  'circle-stroke-color': '#ffffff',
}))

</script>

<template>
  <MglGeoJsonSource source-id="feed-network-routes" :data="lines">
    <MglLineLayer
      layer-id="feed-network-routes-casing"
      :before="before"
      :paint="casingPaint"
      :layout="{ 'line-cap': 'round', 'line-join': 'round', visibility: showRoutes ? 'visible' : 'none' }"
    />
    <MglLineLayer
      layer-id="feed-network-routes-line"
      :before="before"
      :paint="linePaint"
      :layout="{ 'line-cap': 'round', 'line-join': 'round', visibility: showRoutes ? 'visible' : 'none' }"
    />
  </MglGeoJsonSource>

  <MglGeoJsonSource v-if="showStops" source-id="feed-network-stops" :data="stops">
    <MglCircleLayer layer-id="feed-network-stops-circles" :before="before" :paint="stopPaint" />
  </MglGeoJsonSource>

</template>
