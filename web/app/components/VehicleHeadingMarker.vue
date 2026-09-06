<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    color: string
    bearing?: number | null
    /** px size of the arrow glyph */
    size?: number
    /** wrap the arrow in a white circle (used on the vehicle detail map) */
    halo?: boolean
  }>(),
  {bearing: null, size: 18, halo: false},
)

const hasBearing = computed(() => props.bearing != null && Number.isFinite(props.bearing))
const haloSize = computed(() => props.size + 4)
</script>

<template>
  <div
    class="vhm"
    :class="{ 'vhm--halo': halo }"
    :style="halo ? { width: `${haloSize}px`, height: `${haloSize}px` } : undefined"
  >
    <svg
      v-if="hasBearing"
      class="vhm__arrow"
      :width="size"
      :height="size"
      viewBox="0 0 24 24"
      aria-hidden="true"
      :style="{ color, transform: `rotate(${bearing}deg)` }"
    >
      <path
        d="M12 2 L20 21 L12 16 L4 21 Z"
        fill="currentColor"
        stroke="white"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
    </svg>
    <span
      v-else
      class="vhm__dot"
      :style="{ backgroundColor: color, width: `${size - 4}px`, height: `${size - 4}px` }"
    />
  </div>
</template>

<style>
.vhm {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.vhm--halo {
  border-radius: 9999px;
  background: rgb(255 255 255 / 0.9);
  box-shadow: 0 1px 4px rgb(0 0 0 / 0.35);
}

.vhm__arrow {
  transition: transform 0.4s ease-out;
  filter: drop-shadow(0 1px 1px rgb(0 0 0 / 0.35));
}

.vhm--halo .vhm__arrow {
  filter: none;
}

.vhm__dot {
  border-radius: 9999px;
  border: 2px solid white;
  box-shadow: 0 1px 1px rgb(0 0 0 / 0.35);
}
</style>
