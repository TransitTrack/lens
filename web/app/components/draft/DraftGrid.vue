<script setup lang="ts">
/**
 * Timetable grid for a draft route/direction/service. Real implementation
 * lands in Task 6 — this is a placeholder so the editor shell compiles.
 */
defineProps<{
  draftId: string
  routeId: string
  directionId: number | null
  serviceId: string | null
  readOnly: boolean
}>()

defineModel<string | null>('selectedTripId', { default: null })
defineModel<{ tripId: string; stopSequence: number } | null>('activeCell', {
  default: null,
})

defineEmits<{
  'commit-cell': [
    payload: {
      tripId: string
      stopSequence: number
      arrivalSec: number | null
      departureSec: number | null
    },
  ]
  'add-trip': []
}>()
</script>

<template>
  <div class="p-8 text-muted">Grid — task 6</div>
</template>
