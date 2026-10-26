<script setup lang="ts">
const { isLoading } = useLoadingIndicator({
  throttle: 120,
  hideDelay: 180,
})
</script>

<template>
  <Transition name="route-loading-globe">
    <div
      v-if="isLoading"
      class="pointer-events-none fixed top-3 left-1/2 z-60 -translate-x-1/2 border border-default bg-default p-2 shadow-lg"
      role="status"
      aria-live="polite"
      aria-label="Loading page"
    >
      <UIcon name="i-lucide-globe-2" class="size-5 animate-spin text-primary" />
      <span class="sr-only">Loading page</span>
    </div>
  </Transition>
</template>

<style scoped>
.route-loading-globe-enter-active,
.route-loading-globe-leave-active {
  transition: opacity 140ms ease, transform 140ms ease;
}

.route-loading-globe-enter-from,
.route-loading-globe-leave-to {
  opacity: 0;
  transform: translate(-50%, -0.5rem);
}
</style>
