<script setup lang="ts">
import FeedPicker from '../components/FeedPicker.vue'
import VehicleMap from '../components/VehicleMap.vue'
import VehicleList from '../components/VehicleList.vue'
import VehiclePredictionPanel from '../components/VehiclePredictionPanel.vue'
import { useDashboardSelection } from '../composables/useDashboardSelection'
import { useVehiclePolling } from '../composables/useVehiclePolling'

const { selectedFeedCode } = useDashboardSelection()
const { vehicles, loading, error } = useVehiclePolling(selectedFeedCode)

const mapRef = ref<InstanceType<typeof VehicleMap> | null>(null)

function onListSelect(vehicleId: string) {
  mapRef.value?.flyTo(vehicleId)
}
</script>

<template>
  <div class="flex h-screen flex-col">
    <div class="border-default border-b p-4">
      <FeedPicker />
    </div>
    <UAlert
      v-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      title="Vehicle feed unavailable"
      :description="error.message"
      class="m-4"
    />
    <div v-else-if="loading && vehicles.length === 0" class="p-4 text-sm text-muted">
      Loading vehicles…
    </div>
    <div class="flex flex-1 overflow-hidden">
      <div class="flex-1">
        <VehicleMap ref="mapRef" :vehicles="vehicles" />
      </div>
      <div class="border-default w-96 overflow-y-auto border-l">
        <VehicleList :vehicles="vehicles" @select="onListSelect" />
      </div>
    </div>
    <VehiclePredictionPanel />
  </div>
</template>
