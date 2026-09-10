<script setup lang="ts">
import 'maplibre-gl/dist/maplibre-gl.css'
import FeedNetworkLayer from '~/components/FeedNetworkLayer.vue'
import { useMapStyle } from '~/composables/useMapStyle'
import { useFeedGeometry } from '~/composables/useFeedGeometry'
import { extentToBounds } from '~/utils/mapBounds'
import { routeTypeBreakdown } from '~/utils/feedFeatures'
import type { LngLatBoundsExtent } from '~/composables/useFeedExtent'

const props = defineProps<{
  feedCode: string
  extent?: LngLatBoundsExtent | null
  routes: { routeType?: number | null }[]
  fullMapTo?: string
}>()

const MAP_ID = 'feed-covered-area-map'
const mapStyle = useMapStyle()
const map = useMglMap(MAP_ID)

const feedCode = toRef(props, 'feedCode')
const { lines, stops, loading } = useFeedGeometry(feedCode)

const initialBounds = shallowRef(extentToBounds(props.extent))
watch(
  () => props.extent,
  (e) => {
    if (!initialBounds.value) initialBounds.value = extentToBounds(e)
  },
)

const breakdown = computed(() => routeTypeBreakdown(props.routes))
const routeCount = computed(() => props.routes.length)

// Fit once the geometry lands, if we never had a starting extent.
let fitted = false
watch([lines, () => map.isLoaded], () => {
  if (fitted || !map.map || !map.isLoaded || initialBounds.value) return
  const coords = lines.value.features.flatMap((f) =>
    f.geometry.type === 'LineString' ? (f.geometry.coordinates as [number, number][]) : [],
  )
  if (coords.length < 2) return
  const lons = coords.map((c) => c[0])
  const lats = coords.map((c) => c[1])
  map.map.fitBounds(
    [
      [Math.min(...lons), Math.min(...lats)],
      [Math.max(...lons), Math.max(...lats)],
    ],
    { padding: 24, duration: 0 },
  )
  fitted = true
})
</script>

<template>
  <UCard :ui="{ body: 'flex flex-col gap-3 p-0 sm:p-0' }">
    <div class="relative h-64 w-full overflow-hidden rounded-t-lg">
      <MglMap
        :map-key="MAP_ID"
        :map-style="mapStyle"
        :bounds="initialBounds"
        :fit-bounds-options="{ padding: 24, animate: false }"
        :center="[0, 0]"
        :zoom="2"
      >
        <MglNavigationControl />
        <FeedNetworkLayer :lines="lines" :stops="stops" />
      </MglMap>
      <div
        v-if="loading"
        class="absolute inset-x-0 bottom-0 h-0.5 animate-pulse bg-primary/60"
      />
    </div>

    <div class="flex flex-col gap-2 p-4">
      <div class="flex items-center justify-between">
        <div class="text-sm font-medium text-muted">Covered area</div>
        <ULink
          v-if="fullMapTo"
          :to="fullMapTo"
          class="text-xs text-primary hover:underline"
        >
          View full map
        </ULink>
      </div>
      <div class="text-sm text-highlighted">
        {{ routeCount }} route{{ routeCount === 1 ? '' : 's' }}
      </div>
      <div v-if="breakdown.length" class="flex flex-wrap gap-1.5">
        <UBadge
          v-for="b in breakdown"
          :key="b.type"
          color="neutral"
          variant="subtle"
          size="sm"
        >
          {{ b.label }} · {{ b.count }}
        </UBadge>
      </div>
    </div>
  </UCard>
</template>
