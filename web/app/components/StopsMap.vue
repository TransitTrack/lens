<script setup lang="ts">
import 'maplibre-gl/dist/maplibre-gl.css'
import { useMapStyle } from '~/composables/useMapStyle'
import { boundsOf, type LngLat } from '~/utils/vehicleDetail'

const props = defineProps<{
  stops: GeoJSON.FeatureCollection
  /** stopId to highlight + centre on */
  focusId?: string | null
}>()
const emit = defineEmits<{ (e: 'select', stopId: string): void }>()

const MAP_ID = 'stops-map'
const mapStyle = useMapStyle()
const map = useMglMap(MAP_ID)

const allCoords = computed<LngLat[]>(() =>
  props.stops.features
    .map((f) => f.geometry)
    .filter((g): g is GeoJSON.Point => g.type === 'Point')
    .map((g) => [g.coordinates[0]!, g.coordinates[1]!]),
)

function fitAll() {
  const b = boundsOf(allCoords.value)
  if (!b || !map.map || !map.isLoaded) return
  map.map.resize()
  map.map.fitBounds(b, { padding: 40, maxZoom: 15, duration: 600 })
}
watch([() => props.stops, () => map.isLoaded], () => {
  fitAll()
  requestAnimationFrame(fitAll)
}, { immediate: true })

watch(
  () => props.focusId,
  (id) => {
    if (!id || !map.map || !map.isLoaded) return
    const f = props.stops.features.find((x) => x.properties?.stopId === id)
    if (f && f.geometry.type === 'Point') {
      map.map.easeTo({ center: f.geometry.coordinates as [number, number], zoom: 15, duration: 600 })
    }
  },
)

function onClick(e: { features?: { properties?: Record<string, unknown> }[] }) {
  const id = e.features?.[0]?.properties?.stopId
  if (typeof id === 'string') emit('select', id)
}
</script>

<template>
  <div class="h-full w-full">
    <MglMap :map-key="MAP_ID" :map-style="mapStyle" :center="[0, 0]" :zoom="2">
      <MglNavigationControl />
      <MglGeoJsonSource source-id="all-stops" :data="props.stops">
        <MglCircleLayer
          layer-id="all-stops-circles"
          :paint="{
            'circle-radius': ['case', ['==', ['get', 'stopId'], props.focusId ?? ''], 7, 4],
            'circle-color': ['case', ['==', ['get', 'stopId'], props.focusId ?? ''], '#ef4444', '#3b82f6'],
            'circle-stroke-width': 1.5,
            'circle-stroke-color': '#ffffff',
          }"
          @click="onClick"
        />
      </MglGeoJsonSource>
    </MglMap>
  </div>
</template>
