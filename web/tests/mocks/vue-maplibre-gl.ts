/**
 * Test stub for @indoorequal/vue-maplibre-gl (pulled in by nuxt-maplibre).
 *
 * The real package boots a WebGL map through maplibre-gl, whose worker setup
 * throws under happy-dom ("obj must be an instance of Blob"). Every `mountSuspended`
 * test loads the Nuxt app — and therefore the nuxt-maplibre module — so we alias
 * the package to these inert components in vitest.config.ts.
 *
 * MglMap renders its default slot so nested markers still mount; MglMarker renders
 * its `#marker` slot into a probe element; useMap returns a never-loaded registry.
 */
import { defineComponent, h } from 'vue'

export const MglMap = defineComponent({
  name: 'MglMap',
  setup: (_props, { slots }) => () => h('div', { class: 'mgl-map-stub' }, slots.default?.()),
})

export const MglMarker = defineComponent({
  name: 'MglMarker',
  props: { coordinates: { type: [Array, Object], required: true } },
  setup: (_props, { slots }) => () => h('div', { class: 'mgl-marker-stub' }, slots.marker?.()),
})

export const MglNavigationControl = defineComponent({
  name: 'MglNavigationControl',
  render: () => null,
})

export function useMap() {
  return { isMounted: false, isLoaded: false, map: undefined }
}
