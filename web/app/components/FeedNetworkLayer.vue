<script setup lang="ts">
/**
 * GTFS route + stop base layer for a MapLibre map. Mount as a child of
 * <MglMap>. Renders route pattern polylines (coloured by route_color) and a
 * faint stop-dot layer; tune prominence with `dim` when it sits under live
 * markers. Hovering a route highlights every pattern of that route and shows a
 * detail popup.
 */
import type {CircleLayerSpecification, LineLayerSpecification} from 'maplibre-gl'
import type {RouteLineProps} from '~/composables/useFeedGeometry'

const props = withDefaults(
  defineProps<{
    lines: GeoJSON.FeatureCollection
    stops: GeoJSON.FeatureCollection
    /** softer styling for use as an underlay beneath vehicle markers */
    dim?: boolean
    /** hide the stop dots (they can be noisy on a fleet map) */
    showStops?: boolean
    /** enable hover highlight + detail popup on routes */
    interactive?: boolean
    /** id of an existing layer to insert these below (e.g. a live marker layer
     * that must stay on top regardless of mount order) */
    before?: string
  }>(),
  {dim: false, showStops: true, interactive: true},
)

// useMap() with no key resolves the <MglMap> we're nested inside.
const map = useMglMap()

const hoveredRouteId = ref<string | null>(null)
const hoverInfo = ref<RouteLineProps | null>(null)
const hoverAt = ref<[number, number]>([0, 0])

const baseWidth = computed(() => (props.dim ? 1.5 : 2))
const baseOpacity = computed(() => (props.dim ? 0.35 : 0.7))

const linePaint = computed<LineLayerSpecification['paint']>(() => {
  const id = hoveredRouteId.value
  const isHovered = ['==', ['get', 'routeId'], id ?? ' ']
  return {
    'line-color': ['get', 'color'],
    'line-width': id ? ['case', isHovered, 4.5, baseWidth.value] : baseWidth.value,
    'line-opacity': id
      ? ['case', isHovered, 1, Math.min(baseOpacity.value, 0.25)]
      : baseOpacity.value,
  }
})

const stopPaint = computed<CircleLayerSpecification['paint']>(() => ({
  'circle-radius': props.dim ? 2 : 2.5,
  'circle-color': '#3b82f6',
  'circle-opacity': props.dim ? 0.5 : 1,
  'circle-stroke-width': props.dim ? 0 : 0.75,
  'circle-stroke-color': '#ffffff',
}))

interface LayerMouseEvent {
  features?: { properties?: Record<string, unknown> }[]
  lngLat?: { lng: number, lat: number }
}

function onMove(e: LayerMouseEvent) {
  const p = e.features?.[0]?.properties as RouteLineProps | undefined
  if (!p || !e.lngLat) return
  hoveredRouteId.value = p.routeId
  hoverInfo.value = p
  hoverAt.value = [e.lngLat.lng, e.lngLat.lat]
  const canvas = map.map?.getCanvas()
  if (canvas) canvas.style.cursor = 'pointer'
}

function onLeave() {
  hoveredRouteId.value = null
  hoverInfo.value = null
  const canvas = map.map?.getCanvas()
  if (canvas) canvas.style.cursor = ''
}
</script>

<template>
  <MglGeoJsonSource source-id="feed-network-routes" :data="lines">
    <MglLineLayer
      layer-id="feed-network-routes-line"
      :before="before"
      :paint="linePaint"
      :layout="{ 'line-cap': 'round', 'line-join': 'round' }"
    />
    <!-- Wide invisible line so thin routes are easy to hover. -->
    <MglLineLayer
      v-if="interactive"
      layer-id="feed-network-routes-hit"
      :before="before"
      :paint="{ 'line-color': '#000000', 'line-opacity': 0, 'line-width': 16 }"
      @mousemove="onMove"
      @mouseleave="onLeave"
    />
  </MglGeoJsonSource>

  <MglGeoJsonSource v-if="showStops" source-id="feed-network-stops" :data="stops">
    <MglCircleLayer layer-id="feed-network-stops-circles" :before="before" :paint="stopPaint"/>
  </MglGeoJsonSource>

  <MglPopup
    v-if="interactive && hoverInfo"
    :coordinates="hoverAt"
    :close-button="false"
    :close-on-click="false"
    :offset="12"
  >
    <div class="flex min-w-44 max-w-[16rem] flex-col gap-1 p-1">
      <div class="flex items-center gap-2">
        <span
          class="inline-block size-2.5 shrink-0 rounded-full"
          :style="{ backgroundColor: hoverInfo.color }"
        />
        <span class="font-semibold text-highlighted">
          {{ hoverInfo.shortName || hoverInfo.longName || hoverInfo.routeId }}
        </span>
      </div>
      <div v-if="hoverInfo.shortName && hoverInfo.longName" class="text-xs text-muted">
        {{ hoverInfo.longName }}
      </div>
      <div class="flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-dimmed">
        <span v-if="hoverInfo.typeLabel && hoverInfo.typeLabel !== '—'">{{ hoverInfo.typeLabel }}</span>
        <span v-if="hoverInfo.tripCount">{{ hoverInfo.tripCount }} trips</span>
      </div>
      <div v-if="hoverInfo.headsign" class="text-xs text-muted">→ {{ hoverInfo.headsign }}</div>
    </div>
  </MglPopup>
</template>
