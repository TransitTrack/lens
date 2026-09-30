/**
 * Test stub for @indoorequal/vue-maplibre-gl (pulled in by nuxt-maplibre).
 *
 * The real package boots a WebGL map through maplibre-gl, whose worker setup
 * throws under happy-dom ("obj must be an instance of Blob"). Every `mountSuspended`
 * test loads the Nuxt app — and therefore the nuxt-maplibre module — so we alias
 * the package to these inert components in vitest.config.ts.
 *
 * MglMap renders its default slot so nested markers/sources still mount; MglMarker
 * renders its `#marker` slot into a probe element; MglGeoJsonSource/MglSymbolLayer
 * stand in for VehicleMap's/StopsMap's canvas-icon symbol layers, rendering one
 * probe element per GeoJSON feature and re-emitting `click` with the clicked
 * feature, the same shape maplibre-gl passes to a real layer's click handler;
 * useMap returns a never-loaded registry.
 */
import { defineComponent, h, inject, provide, type InjectionKey, type PropType } from 'vue'

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

export const MglPopup = defineComponent({
  name: 'MglPopup',
  props: { coordinates: { type: [Array, Object], required: true } },
  setup: (_props, { slots }) => () => h('div', { class: 'mgl-popup-stub' }, slots.default?.()),
})

const geoJsonDataKey: InjectionKey<() => GeoJSON.FeatureCollection | undefined> =
  Symbol('mgl-geojson-data')

export const MglGeoJsonSource = defineComponent({
  name: 'MglGeoJsonSource',
  props: {
    sourceId: { type: String, required: true },
    data: { type: Object as PropType<GeoJSON.FeatureCollection>, required: true },
  },
  setup(props, { slots }) {
    provide(geoJsonDataKey, () => props.data)
    return () =>
      h(
        'div',
        { class: 'mgl-geojson-source-stub', 'data-source-id': props.sourceId },
        slots.default?.(),
      )
  },
})

export const MglSymbolLayer = defineComponent({
  name: 'MglSymbolLayer',
  props: {
    layerId: { type: String, required: true },
    layout: { type: Object, default: () => ({}) },
  },
  emits: ['click'],
  setup(props, { emit }) {
    const getData = inject(geoJsonDataKey, () => undefined)
    return () =>
      h(
        'div',
        { class: 'mgl-symbol-layer-stub', 'data-layer-id': props.layerId },
        (getData()?.features ?? []).map((feature, i) =>
          h('div', {
            key: i,
            class: 'mgl-symbol-feature-stub',
            onClick: () => emit('click', { features: [feature] }),
          }),
        ),
      )
  },
})

export function useMap() {
  return { isMounted: false, isLoaded: false, map: undefined }
}
