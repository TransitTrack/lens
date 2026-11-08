<script setup lang="ts">
import MapToolLayers from '~/components/map/MapToolLayers.vue'
import MapToolbox from '~/components/map/MapToolbox.vue'
import VehicleHeadingMarker from '~/components/map/VehicleHeadingMarker.vue'
import {hideBasemapLabels, useMapStyle} from '~/composables/useMapStyle'
import {boundsOf} from '~/utils/vehicleDetail'
import {extentToBounds} from '~/utils/mapBounds'
import type {LngLatBoundsExtent} from '~/composables/useFeedExtent'

const props = withDefaults(
  defineProps<{
    line: LngLat[]
    stops: GeoJSON.FeatureCollection
    vehicle: { lng: number, lat: number, bearing: number | null } | null
    routeColor: string
    /** recent AVL fixes, oldest → newest, as [lon, lat] */
    trail?: LngLat[]
    /** keep the map centred on the vehicle as it moves */
    follow?: boolean
    /** agency service area — frames the map on load before the route line arrives */
    extent?: LngLatBoundsExtent | null
    /** stopId to highlight and centre on */
    focusId?: string | null
  }>(),
  {trail: () => [], follow: false, extent: null, focusId: null},
)

const MAP_ID = 'vehicle-route-map'
const mapStyle = useMapStyle()

const map = useMglMap(MAP_ID)
const tools = useMapTools()
const visibleLayers = reactive({ routeLine: true, stops: true, trail: true, vehicle: true })
const hideStreetNames = () => hideBasemapLabels(map.map)
const routeColorMode = ref<'official' | 'distinct'>('official')
const displayRouteColor = computed(() =>
  routeColorMode.value === 'distinct' ? '#2563eb' : props.routeColor,
)

// maplibre doesn't observe its own container, so a splitter drag or any other
// layout change that resizes this element without resizing the window leaves
// the canvas the wrong size until the next full-window resize.
const mapContainer = useTemplateRef('mapContainer')
let resizeObserver: ResizeObserver | undefined
onMounted(() => {
  resizeObserver = new ResizeObserver(() => map.map?.resize())
  if (mapContainer.value) resizeObserver.observe(mapContainer.value)
})
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  map.map?.off('style.load', hideStreetNames)
})
watch(
  () => map.isLoaded,
  (loaded) => {
    if (!loaded) return
    hideStreetNames()
    map.map?.on('style.load', hideStreetNames)
  },
  { immediate: true },
)

// Capture the agency extent once — see StopsMap for why binding it live yanks
// the view on every poll.
const initialBounds = shallowRef(extentToBounds(props.extent))
watch(
  () => props.extent,
  (e) => {
    if (!initialBounds.value) initialBounds.value = extentToBounds(e)
  },
)

// Stable signature of the drawn route — endpoints + length. Changes when the
// route/trip changes, not when a poll rebuilds the same line array.
const lineKey = computed(() => {
  const l = props.line
  if (!l.length) return props.vehicle ? 'v' : ''
  return `${l.length}:${l[0]}:${l[l.length - 1]}`
})

const lineData = computed<GeoJSON.Feature>(() => ({
  type: 'Feature',
  properties: {},
  geometry: {type: 'LineString', coordinates: props.line},
}))

const trailData = computed<GeoJSON.Feature>(() => ({
  type: 'Feature',
  properties: {},
  geometry: {type: 'LineString', coordinates: props.trail},
}))

// First fit is instant — the map opened on the agency extent via `:bounds`.
let firstFit = true

function fit() {
  const b = boundsOf(
    props.line.length
      ? props.line
      : props.vehicle
        ? [[props.vehicle.lng, props.vehicle.lat]]
        : [],
  )
  if (!b || !map.map || !map.isLoaded) return
  map.map.fitBounds(b, {padding: 48, duration: firstFit ? 0 : 800, maxZoom: 16})
  firstFit = false
}

watch([lineKey, () => map.isLoaded], fit, {immediate: true})

function focusStop() {
  const id = props.focusId
  if (!id || !map.map || !map.isLoaded) return
  const stop = props.stops.features.find((feature) => feature.properties?.stopId === id)
  if (!stop || stop.geometry.type !== 'Point') return
  map.map.easeTo({
    center: stop.geometry.coordinates as [number, number],
    zoom: Math.max(map.map.getZoom(), 15),
    duration: 600,
  })
}

watch([() => props.focusId, () => map.isLoaded], focusStop, {immediate: true})

watch(
  () => props.vehicle,
  (v) => {
    if (props.follow && v && map.map && map.isLoaded) {
      map.map.easeTo({center: [v.lng, v.lat], duration: 600})
    }
  },
)

function onMapClick(e: { event?: { lngLat?: { lng: number; lat: number }; originalEvent?: MouseEvent } }) {
  const lngLat = e.event?.lngLat
  if (!lngLat) return
  tools.onMapClick(
    [lngLat.lng, lngLat.lat],
    e.event?.originalEvent?.detail === 2,
  )
}

watch(() => tools.activeTool.value, (active) => {
  const canvas = map.map?.getCanvas()
  if (canvas) canvas.style.cursor = active ? 'crosshair' : ''
})
</script>

<template>
  <div ref="mapContainer" class="relative h-full w-full">
    <MglMap
      :map-key="MAP_ID"
      :map-style="mapStyle"
      :bounds="initialBounds"
      :attribution-control="false"
      :fit-bounds-options="{padding: 40, animate: false}"
      :center="[0, 0]"
      :zoom="2"
      @map:click="onMapClick"
    >
<!--      <MglNavigationControl/>-->

      <MglGeoJsonSource v-if="trail.length > 1" source-id="avl-trail" :data="trailData">
        <MglLineLayer
          layer-id="avl-trail-line"
          :paint="{ 'line-color': '#38bdf8', 'line-width': 2, 'line-opacity': 0.7, 'line-dasharray': [2, 2] }"
          :layout="{ 'line-cap': 'round', 'line-join': 'round', visibility: visibleLayers.trail ? 'visible' : 'none' }"
        />
      </MglGeoJsonSource>

      <MglGeoJsonSource source-id="route-line" :data="lineData">
        <MglLineLayer
          layer-id="route-line-casing"
          :paint="{ 'line-color': '#0f172a', 'line-width': 7, 'line-opacity': 0.35 }"
          :layout="{ 'line-cap': 'round', 'line-join': 'round', visibility: visibleLayers.routeLine ? 'visible' : 'none' }"
        />
        <MglLineLayer
          layer-id="route-line-main"
          :paint="{ 'line-color': displayRouteColor, 'line-width': 4 }"
          :layout="{ 'line-cap': 'round', 'line-join': 'round', visibility: visibleLayers.routeLine ? 'visible' : 'none' }"
        />
      </MglGeoJsonSource>

      <MglGeoJsonSource source-id="route-stops" :data="props.stops">
        <MglCircleLayer
          v-if="props.focusId"
          layer-id="route-focused-stop-halo"
          :layout="{ visibility: visibleLayers.stops ? 'visible' : 'none' }"
          :filter="['==', ['get', 'stopId'], props.focusId]"
          :paint="{
            'circle-radius': 13,
            'circle-color': displayRouteColor,
            'circle-opacity': 0.2,
            'circle-stroke-width': 2,
            'circle-stroke-color': displayRouteColor,
          }"
        />
        <MglCircleLayer
          layer-id="route-stops-circles"
          :layout="{ visibility: visibleLayers.stops ? 'visible' : 'none' }"
          :paint="{
            'circle-radius': ['match', ['get', 'state'], 'current', 8, 4],
            'circle-color': [
              'match',
              ['get', 'state'],
              'passed', '#94a3b8',
              'current', displayRouteColor,
              '#475569',
            ],
            'circle-stroke-width': ['match', ['get', 'state'], 'current', 2.5, 1.5],
            'circle-stroke-color': '#ffffff',
          }"
        />
        <MglCircleLayer
          v-if="props.focusId"
          layer-id="route-focused-stop-core"
          :layout="{ visibility: visibleLayers.stops ? 'visible' : 'none' }"
          :filter="['==', ['get', 'stopId'], props.focusId]"
          :paint="{
            'circle-radius': 7,
            'circle-color': displayRouteColor,
            'circle-stroke-width': 2.5,
            'circle-stroke-color': '#ffffff',
          }"
        />
        <MglSymbolLayer
          layer-id="route-stop-labels"
          :layout="{
            'text-field': ['coalesce', ['get', 'label'], ['get', 'name']],
            'text-size': 11,
            'text-font': ['noto_sans_bold'],
            'text-anchor': 'top',
            'text-offset': [0, 0.9],
            'text-max-width': 14,
            'text-allow-overlap': false,
            'text-ignore-placement': false,
            visibility: visibleLayers.stops ? 'visible' : 'none',
          }"
          :paint="{
            'text-color': '#1e293b',
            'text-halo-color': '#ffffff',
            'text-halo-width': 1.5,
          }"
        />
      </MglGeoJsonSource>

      <MglMarker v-if="props.vehicle && visibleLayers.vehicle" :coordinates="[props.vehicle.lng, props.vehicle.lat]">
        <template #marker>
          <VehicleHeadingMarker
            :color="displayRouteColor"
            :bearing="props.vehicle.bearing"
            :size="22"
            halo
          />
        </template>
      </MglMarker>
      <MapToolLayers :map-id="MAP_ID" :tools="tools" />
    </MglMap>
    <MapToolbox
      :tools="tools"
      route-colors-available
      :route-color-mode="routeColorMode"
      :layers="[
        { id: 'routeLine', label: $t('map.layer.routeLine'), enabled: visibleLayers.routeLine },
        { id: 'stops', label: $t('map.layer.stops'), enabled: visibleLayers.stops },
        { id: 'trail', label: $t('map.layer.avlTrail'), enabled: visibleLayers.trail },
        { id: 'vehicle', label: $t('map.layer.vehicleMarker'), enabled: visibleLayers.vehicle },
      ]"
      @update:layer="({ id, enabled }) => {
        if (id === 'routeLine') visibleLayers.routeLine = enabled
        if (id === 'stops') visibleLayers.stops = enabled
        if (id === 'trail') visibleLayers.trail = enabled
        if (id === 'vehicle') visibleLayers.vehicle = enabled
      }"
      @update:route-color-mode="routeColorMode = $event"
    />
<!--
    <div class="pointer-events-none absolute bottom-3 left-3 flex flex-col gap-1.5 rounded-md border border-default bg-default/90 px-3 py-2.5 text-xs shadow-sm backdrop-blur">
      <div class="flex items-center gap-2">
        <span class="h-0.75 w-6 rounded-full" :style="{ backgroundColor: props.routeColor }"/>
        <span class="text-muted">Scheduled route</span>
      </div>
      <div v-if="props.trail.length > 1" class="flex items-center gap-2">
        <span class="w-6 border-t-2 border-dashed border-sky-400"/>
        <span class="text-muted">Recent GPS reports · oldest to newest</span>
      </div>
      <div class="flex items-center gap-2">
        <span class="size-2 rounded-full border-2 border-white bg-slate-600 ring-1 ring-slate-400"/>
        <span class="text-muted">Stops</span>
      </div>
      <div v-if="props.vehicle" class="flex items-center gap-2">
        <span class="flex size-4 items-center justify-center rounded-full bg-white shadow ring-1 ring-black/20">
          <UIcon name="i-lucide-navigation" class="size-2.5" :style="{ color: props.routeColor }"/>
        </span>
        <span class="text-muted">Live vehicle position</span>
      </div>
    </div>
-->
  </div>
</template>
