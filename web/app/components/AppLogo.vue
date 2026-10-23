<script setup lang="ts">
withDefaults(
  defineProps<{
    /** "TransitTrack" wordmark size, px */
    fontSize?: number
    /** stack the icon above the wordmark */
    stacked?: boolean
    /** show the "Mobility Workflow Suite" kicker */
    showSubtitle?: boolean
  }>(),
  {
    fontSize: 32,
    stacked: false,
    showSubtitle: true,
  },
)
</script>

<template>
  <div
    :class="[
      'flex select-none items-center font-sans transition-colors duration-200',
      stacked ? 'flex-col gap-3 text-center' : 'flex-row gap-2',
    ]"
  >
    <div class="flex flex-col leading-none align-middle">
      <h1
        :style="{ fontSize: fontSize + 'px' }"
        class="uppercase tracking-tight text-slate-800 dark:text-slate-100"
      >
        <span class="font-black">Lens</span>
        <span class="font-black text-primary">App</span>
      </h1>
      <p
        v-if="showSubtitle"
        :style="{ fontSize: fontSize * 0.3 + 'px' }"
        class="mt-1 font-medium uppercase tracking-widest text-slate-500 dark:text-slate-400"
      >
        Mobility Workflow Suite
      </p>
    </div>
  </div>
</template>

<style scoped>
/* A light pulse travels each track from the arrow-head, through the node, to
   the opposite arrow-head — reads as movement along the route. */
.tt-flow {
  stroke-dasharray: 12 64;
  animation: tt-flow 2.4s linear infinite;
}

.tt-flow-delay {
  animation-delay: -1.2s;
}

.tt-node {
  transform-box: fill-box;
  transform-origin: center;
  animation: tt-node-pulse 2.4s ease-in-out infinite;
}

.tt-node-delay {
  animation-delay: -1.2s;
}

@keyframes tt-flow {
  to {
    stroke-dashoffset: -76;
  }
}

.tt-logo:hover .tt-flow {
  animation-duration: 1s;
}

@media (prefers-reduced-motion: reduce) {
  .tt-flow,
  .tt-node {
    animation: none;
  }

  .tt-flow {
    stroke-dasharray: none;
    opacity: 0;
  }
}
</style>
