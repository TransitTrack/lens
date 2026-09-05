<script setup lang="ts">
import FeedPicker from '../components/FeedPicker.vue'
import VehicleMap from '../components/VehicleMap.vue'
import VehicleList from '../components/VehicleList.vue'
import VehiclePredictionPanel from '../components/VehiclePredictionPanel.vue'
import { useDashboardSelection } from '../composables/useDashboardSelection'
import { useVehiclePolling } from '../composables/useVehiclePolling'

const { selectedFeedCode } = useDashboardSelection()
const { vehicles } = useVehiclePolling(selectedFeedCode)

const mapRef = ref<InstanceType<typeof VehicleMap> | null>(null)

function onListSelect(vehicleId: string) {
  mapRef.value?.flyTo(vehicleId)
}
</script>

<template>
  <div class="flex h-screen flex-col">
    <div class="border-b p-4">
      <FeedPicker />
    </div>
    <div class="flex flex-1 overflow-hidden">
      <div class="flex-1">
        <VehicleMap ref="mapRef" :vehicles="vehicles" />
      </div>
      <div class="w-96 overflow-y-auto border-l">
        <VehicleList :vehicles="vehicles" @select="onListSelect" />
      </div>
    </div>
    <VehiclePredictionPanel />
  </div>
</template>
