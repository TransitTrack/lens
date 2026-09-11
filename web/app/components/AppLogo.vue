<script setup lang="ts">
withDefaults(
  defineProps<{
    /** SVG icon square, px */
    iconSize?: number
    /** "TransitTrack" wordmark size, px */
    fontSize?: number
    /** hide the wordmark, render the icon only */
    iconOnly?: boolean
    /** stack the icon above the wordmark */
    stacked?: boolean
    /** show the "Mobility Workflow Suite" kicker */
    showSubtitle?: boolean
  }>(),
  {
    iconSize: 48,
    fontSize: 24,
    iconOnly: false,
    stacked: false,
    showSubtitle: true,
  },
)

const TRACK_1 = 'M 90,40 L 40,40 L 40,90'
const TRACK_2 = 'M 110,55 L 65,55 L 65,100'
</script>

<template>
  <div
    :class="[
      'flex select-none items-center font-sans transition-colors duration-200',
      stacked ? 'flex-col gap-3 text-center' : 'flex-row gap-2',
    ]"
  >
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 120 120"
      :style="{ width: iconSize + 'px', height: iconSize + 'px' }"
      class="tt-logo shrink-0"
      aria-hidden="true"
    >
      <g
        stroke-width="8"
        stroke-linecap="round"
        stroke-linejoin="round"
        fill="none"
      >
        <!-- Track 1 -->
        <path :d="TRACK_1" class="stroke-indigo-600 dark:stroke-indigo-400"/>
        <path
          :d="TRACK_1"
          class="tt-flow stroke-indigo-300 dark:stroke-indigo-200"
          stroke-width="5"
        />
        <path d="M 82,32 L 92,40 L 82,48" class="stroke-indigo-600 dark:stroke-indigo-400"/>
        <path d="M 32,82 L 40,92 L 48,82" class="stroke-indigo-600 dark:stroke-indigo-400"/>
        <circle
          cx="40"
          cy="40"
          r="5"
          fill="white"
          stroke-width="6"
          class="tt-node stroke-indigo-600 dark:stroke-indigo-400"
        />

        <!-- Track 2 -->
        <path :d="TRACK_2" class="stroke-indigo-600 dark:stroke-indigo-400"/>
        <path
          :d="TRACK_2"
          class="tt-flow tt-flow-delay stroke-indigo-300 dark:stroke-indigo-200"
          stroke-width="5"
        />
        <path d="M 102,47 L 112,55 L 102,63" class="stroke-indigo-600 dark:stroke-indigo-400"/>
        <path d="M 57,92 L 65,102 L 73,92" class="stroke-indigo-600 dark:stroke-indigo-400"/>
        <circle
          cx="65"
          cy="55"
          r="5"
          fill="white"
          stroke-width="6"
          class="tt-node tt-node-delay stroke-indigo-600 dark:stroke-indigo-400"
        />
      </g>
    </svg>

    <div v-if="!iconOnly" class="flex flex-col leading-none align-middle mt-2">
      <h1
        :style="{ fontSize: fontSize + 'px' }"
        class="uppercase tracking-tighter text-slate-800 dark:text-slate-100"
      >
        <span class="font-black">Transit</span>
        <span class="font-light">Track</span>
      </h1>
      <p
        v-if="showSubtitle"
        :style="{ fontSize: fontSize * 0.4 + 'px' }"
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

@keyframes tt-node-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.55;
    transform: scale(0.88);
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
