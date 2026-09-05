<script setup lang="ts">
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import type { VehicleRow } from '../composables/useVehiclePolling'
import { colorForVehicle } from '../utils/vehicleMarker'
import { useDashboardSelection } from '../composables/useDashboardSelection'

const props = defineProps<{ vehicles: VehicleRow[] }>()

const mapContainer = ref<HTMLDivElement | null>(null)
let map: InstanceType<typeof maplibregl.Map> | null = null
const markers = new Map<string, InstanceType<typeof maplibregl.Marker>>()

const { selectVehicle } = useDashboardSelection()

function syncMarkers(vehicles: VehicleRow[]) {
  if (!map) return
  const seen = new Set<string>()
  for (const v of vehicles) {
    seen.add(v.vehicleId)
    const lngLat: [number, number] = [v.position.lon, v.position.lat]
    const existing = markers.get(v.vehicleId)
    if (existing) {
      existing.setLngLat(lngLat)
      existing.getElement().style.backgroundColor = colorForVehicle(v.matched, v.stale)
    } else {
      const el = document.createElement('div')
      el.className = 'vehicle-marker'
      el.style.backgroundColor = colorForVehicle(v.matched, v.stale)
      el.addEventListener('click', () => selectVehicle(v.vehicleId))
      const marker = new maplibregl.Marker({ element: el }).setLngLat(lngLat).addTo(map)
      markers.set(v.vehicleId, marker)
    }
  }
  for (const [id, marker] of markers) {
    if (!seen.has(id)) {
      marker.remove()
      markers.delete(id)
    }
  }
}

onMounted(() => {
  if (!mapContainer.value) return
  map = new maplibregl.Map({
    container: mapContainer.value,
    style: 'https://demotiles.maplibre.org/style.json',
    center: [0, 0],
    zoom: 2,
  })
  syncMarkers(props.vehicles)
})

onBeforeUnmount(() => {
  markers.forEach((m) => m.remove())
  markers.clear()
  map?.remove()
  map = null
})

watch(
  () => props.vehicles,
  (vehicles) => {
    syncMarkers(vehicles)
  },
  { deep: true },
)

function flyTo(vehicleId: string) {
  const v = props.vehicles.find((x) => x.vehicleId === vehicleId)
  if (v && map) map.flyTo({ center: [v.position.lon, v.position.lat], zoom: 14 })
}

defineExpose({ flyTo })
</script>

<template>
  <div ref="mapContainer" class="h-full w-full" />
</template>

<style>
.vehicle-marker {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid white;
  cursor: pointer;
}
</style>
