<script setup lang="ts">
/**
 * GTFS route + stop base layer for a MapLibre map. Mount as a child of
 * <MglMap>. Renders route pattern polylines (coloured by route_color) and a
 * faint stop-dot layer; tune prominence with `dim` when it sits under live
 * markers.
 */
import type {
  CircleLayerSpecification,
  LineLayerSpecification,
  MapLayerMouseEvent,
  SymbolLayerSpecification,
} from 'maplibre-gl'

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
const emit = defineEmits<{
  clusterClick: [cluster: { id: number; center: LngLat }]
}>()

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
  'circle-radius': props.dim ? 3 : 4,
  'circle-color': colorMode.value === 'dark' ? '#0b0d0e' : '#f8fafc',
  'circle-opacity': 1,
  'circle-stroke-width': 0.5,
  'circle-stroke-color': colorMode.value === 'dark' ? '#999' : '#fff',
}))

const routeLabelLayout = computed<SymbolLayerSpecification['layout']>(() => ({
  'symbol-placement': 'line',
  'symbol-spacing': 350,
  'text-field': ['get', 'shortName'],
  'text-size': props.dim ? 10 : 11,
  // Matches the glyph fontstack the VersaTiles basemap styles actually serve
  // (confirmed against their style.json) — an unregistered name like "Open
  // Sans SemiBold" 404s per-glyph-range and silently renders no text.
  'text-font': ['noto_sans_bold'],
  'text-rotation-alignment': 'map',
  'text-keep-upright': true,
  // Lifts the label above the line instead of letting the stroke cut
  // through the glyphs — offset is in the line's local (rotated) frame.
  'text-offset': [0, -0.9],
  visibility: props.showRoutes ? 'visible' : 'none',
}))

const routeLabelPaint = computed<SymbolLayerSpecification['paint']>(() => ({
  'text-color': ['get', 'color'],
  'text-halo-color': colorMode.value === 'dark' ? '#0b0d0e' : '#ffffff',
  'text-halo-width': 1.4,
  'text-opacity': props.dim ? 0.75 : 1,
}))

function onClusterClick(e: MapLayerMouseEvent) {
  const cluster = e.features?.[0]
  const id = cluster?.properties?.cluster_id
  if (typeof id !== 'number' || !cluster || cluster.geometry.type !== 'Point') return

  emit('clusterClick', {
    id,
    center: cluster.geometry.coordinates as LngLat,
  })
}
</script>

<template>
  <MglGeoJsonSource source-id="feed-network-routes" :data="lines">
    <MglLineLayer
      layer-id="feed-network-routes-casing"
      :before="before"
      :paint="casingPaint"
      :layout="{
        'line-cap': 'round',
        'line-join': 'round',
        visibility: showRoutes ? 'visible' : 'none',
      }"
    />
    <MglLineLayer
      layer-id="feed-network-routes-line"
      :before="before"
      :paint="linePaint"
      :layout="{
        'line-cap': 'round',
        'line-join': 'round',
        visibility: showRoutes ? 'visible' : 'none',
      }"
    />
    <MglSymbolLayer
      layer-id="feed-network-routes-labels"
      :before="before"
      :minzoom="12.5"
      :filter="['!=', ['get', 'shortName'], '']"
      :layout="routeLabelLayout"
      :paint="routeLabelPaint"
    />
  </MglGeoJsonSource>

  <MglGeoJsonSource
    v-if="showStops"
    source-id="feed-network-stops"
    :data="stops"
    cluster
    :cluster-radius="48"
    :cluster-max-zoom="14"
  >
    <MglCircleLayer
      layer-id="feed-network-stop-clusters"
      :before="before"
      :filter="['has', 'point_count']"
      :paint="{
        'circle-radius': ['step', ['get', 'point_count'], 13, 10, 16, 50, 20, 250, 24],
        'circle-color': colorMode.value === 'dark' ? '#0b0d0e' : '#f8fafc',
        'circle-opacity': 0.8,
        'circle-stroke-width': 1,
        'circle-stroke-color': '#ffffff',
      }"
      @click="onClusterClick"
    />
    <MglSymbolLayer
      layer-id="feed-network-stop-cluster-counts"
      :before="before"
      :filter="['has', 'point_count']"
      :layout="{
        'text-field': ['get', 'point_count_abbreviated'],
        'text-font': ['noto_sans_bold'],
        'text-size': 12,
        'text-allow-overlap': true,
      }"
      :paint="{ 'text-color': '#ffffff' }"
    />
    <MglCircleLayer
      layer-id="feed-network-stops-circles"
      :before="before"
      :filter="['!', ['has', 'point_count']]"
      :paint="stopPaint"
    />
  </MglGeoJsonSource>
</template>
