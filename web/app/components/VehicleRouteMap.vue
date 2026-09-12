<script setup lang="ts">
import VehicleHeadingMarker from './VehicleHeadingMarker.vue'
import {useMapStyle} from '../composables/useMapStyle'
import {boundsOf} from '../utils/vehicleDetail'
import {extentToBounds} from '../utils/mapBounds'
import type {LngLatBoundsExtent} from '../composables/useFeedExtent'

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
  }>(),
  {trail: () => [], follow: false, extent: null},
)

const MAP_ID = 'vehicle-route-map'
const mapStyle = useMapStyle()

const map = useMglMap(MAP_ID)

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

watch(
  () => props.vehicle,
  (v) => {
    if (props.follow && v && map.map && map.isLoaded) {
      map.map.easeTo({center: [v.lng, v.lat], duration: 600})
    }
  },
)
</script>

<template>
  <div class="h-full w-full">
    <MglMap
      :map-key="MAP_ID"
      :map-style="mapStyle"
      :bounds="initialBounds"
      :fit-bounds-options="{padding: 40, animate: false}"
      :center="[0, 0]"
      :zoom="2"
    >
      <MglNavigationControl/>

      <MglGeoJsonSource v-if="trail.length > 1" source-id="avl-trail" :data="trailData">
        <MglLineLayer
          layer-id="avl-trail-line"
          :paint="{ 'line-color': '#38bdf8', 'line-width': 2, 'line-opacity': 0.7, 'line-dasharray': [2, 2] }"
          :layout="{ 'line-cap': 'round', 'line-join': 'round' }"
        />
      </MglGeoJsonSource>

      <MglGeoJsonSource source-id="route-line" :data="lineData">
        <MglLineLayer
          layer-id="route-line-casing"
          :paint="{ 'line-color': '#0f172a', 'line-width': 7, 'line-opacity': 0.35 }"
          :layout="{ 'line-cap': 'round', 'line-join': 'round' }"
        />
        <MglLineLayer
          layer-id="route-line-main"
          :paint="{ 'line-color': props.routeColor, 'line-width': 4 }"
          :layout="{ 'line-cap': 'round', 'line-join': 'round' }"
        />
      </MglGeoJsonSource>

      <MglGeoJsonSource source-id="route-stops" :data="props.stops">
        <MglCircleLayer
          layer-id="route-stops-circles"
          :paint="{
            'circle-radius': ['match', ['get', 'state'], 'current', 8, 4],
            'circle-color': [
              'match',
              ['get', 'state'],
              'passed', '#94a3b8',
              'current', props.routeColor,
              '#475569',
            ],
            'circle-stroke-width': ['match', ['get', 'state'], 'current', 2.5, 1.5],
            'circle-stroke-color': '#ffffff',
          }"
        />
        <MglSymbolLayer
          layer-id="route-stop-labels"
          :layout="{
            'text-field': ['get', 'name'],
            'text-size': 11,
            'text-font': ['Open Sans SemiBold'],
            'text-anchor': 'top',
            'text-offset': [0, 0.9],
            'text-max-width': 14,
            'text-allow-overlap': false,
            'text-ignore-placement': false,
          }"
          :paint="{
            'text-color': '#1e293b',
            'text-halo-color': '#ffffff',
            'text-halo-width': 1.5,
          }"
        />
      </MglGeoJsonSource>

      <MglMarker v-if="props.vehicle" :coordinates="[props.vehicle.lng, props.vehicle.lat]">
        <template #marker>
          <VehicleHeadingMarker
            :color="props.routeColor"
            :bearing="props.vehicle.bearing"
            :size="22"
            halo
          />
        </template>
      </MglMarker>
    </MglMap>

    <div class="pointer-events-none absolute bottom-3 left-3 flex flex-col gap-1.5 rounded-lg border border-default bg-default/90 px-3 py-2.5 text-xs shadow-sm backdrop-blur">
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
  </div>
</template>
