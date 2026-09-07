<script setup lang="ts">
/**
 * Right-hand inspector rail for the selected trip. Real implementation lands
 * in Task 7 — this is a placeholder so the editor shell compiles.
 */
defineProps<{
  draftId: string
  routeId: string
  directionId: number | null
  serviceId: string | null
  selectedTripId: string | null
  readOnly: boolean
}>()
</script>

<template>
  <div class="w-72 shrink-0 border-l border-default p-4 text-muted">
    Rail — task 7
  </div>
</template>
