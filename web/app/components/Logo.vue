<template>
  <div class="logo-container" :style="{ width: size + 'px', height: 'auto' }">
    <svg
      :viewBox="collapsed ? '0 0 370 240' : '0 0 970 280'"
      width="100%"
      height="100%"
      fill="none"
      class="transit-logo-svg"
    >
      <defs>
        <!-- Structural background tracking guidelines (Ultra-Thick) -->
        <path id="heavyPath1" d="M 35,65 L 140,20 L 240,150 L 345,95"/>
        <path id="heavyPath2" d="M 35,115 L 140,115 L 240,65 L 345,135"/>
        <path id="heavyPath3" d="M 35,165 L 140,180 L 210,95 L 345,185"/>
      </defs>

      <!-- BRAND LOGO ICON (Maximum Thickness Grid) -->
      <g transform="translate(10, 15)">
        <!-- Static High-Visibility Base Layers -->
        <use href="#heavyPath1" stroke="#cbd5e1" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
        <use href="#heavyPath2" stroke="#cbd5e1" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
        <use href="#heavyPath3" stroke="#cbd5e1" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>

        <!-- Line 1: Ultra-Thick Dynamic Flow -->
        <use href="#heavyPath1" class="flow-line line-slow" :stroke="accentColor" stroke-width="12" stroke-linecap="round"
             stroke-linejoin="round"/>

        <!-- Line 2: Ultra-Thick Dynamic Flow -->
        <use href="#heavyPath2" class="flow-line line-fast" :stroke="accentColor" stroke-width="12" stroke-linecap="round"
             stroke-linejoin="round"/>

        <!-- Line 3: Ultra-Thick Dynamic Flow -->
        <use href="#heavyPath3" class="flow-line line-normal" :stroke="accentColor" stroke-width="12" stroke-linecap="round"
             stroke-linejoin="round"/>

        <!-- Heavy Junction Hubs (Thick white shields to block background lines completely) -->
        <circle cx="140" cy="115" r="14" fill="#0f172a" stroke="#ffffff" stroke-width="5"/>
        <circle cx="240" cy="65" r="14" fill="#0f172a" stroke="#ffffff" stroke-width="5"/>

        <!-- Key Tracking Terminals with Deep Contours -->
        <circle cx="345" cy="95" r="10" :fill="accentColor" stroke="#ffffff" stroke-width="3"/>
        <circle cx="345" cy="135" r="10" :fill="accentColor" stroke="#ffffff" stroke-width="3"/>
        <circle cx="345" cy="185" r="10" :fill="accentColor" stroke="#ffffff" stroke-width="3"/>
      </g>

      <!-- BRAND TYPOGRAPHY (Max Impact Weight) -->
      <g v-if="!collapsed" transform="translate(410, 148)">
        <text class="font-ultra-black" x="0" y="0" fill="#0f172a">TRANSIT</text>
        <text class="font-bold-accent" x="290" y="0" :fill="accentColor">TRACK</text>
        <text class="font-sub-heavy" x="5" y="44" fill="#334155">ROUTE ANALYSIS &amp; PREDICTIONS</text>
      </g>
    </svg>
  </div>
</template>

<script setup>
defineProps({
  size: {
    type: [String, Number],
    default: 590
  },
  collapsed: {
    type: Boolean,
    default: false
  },
  accentColor: {
    type: String,
    default: '#0284c7'
  }
});
</script>

<style scoped>
.logo-container {
  display: inline-block;
  vertical-align: middle;
  padding-top: 20px;
  padding-bottom: 5px;
}

/* Max weight system typography setup to rival native app headers */
.font-ultra-black {
  font-family: Arial Black, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  font-weight: 900;
  font-size: 72px;
  letter-spacing: -2.5px;
}

.font-bold-accent {
  font-family: Arial Black, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  font-weight: 900;
  font-size: 72px;
  letter-spacing: -2.5px;
}

.font-sub-heavy {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  font-weight: 800; /* Extra bold subtitle tracking layout */
  font-size: 15px;
  letter-spacing: 5px;
}

/* Solid high-visibility dash patterns for robust interface presence */
.flow-line {
  stroke-dasharray: 25 30;
}

.line-slow {
  animation: transitFlowPattern 4.8s linear infinite;
}

.line-fast {
  animation: transitFlowPattern 2.2s linear infinite;
}

.line-normal {
  animation: transitFlowPattern 3.5s linear infinite;
}

@keyframes transitFlowPattern {
  0% {
    stroke-dashoffset: 110;
  }
  100% {
    stroke-dashoffset: 0;
  }
}
</style>
