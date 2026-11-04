import { addComponent, addImports, defineNuxtModule } from '@nuxt/kit'

export interface ModuleOptions {
  css: string | string[] | false
}

export const components = [
  'MglImage',
  'MglMap',
  'MglMarker',
  'MglPopup',
  'MglAttributionControl',
  'MglCustomControl',
  'MglFullscreenControl',
  'MglGeolocateControl',
  'MglLogoControl',
  'MglNavigationControl',
  'MglScaleControl',
  'MglCanvasSource',
  'MglGeoJsonSource',
  'MglImageSource',
  'MglRasterDemSource',
  'MglRasterSource',
  'MglVectorSource',
  'MglVideoSource',
  'MglBackgroundLayer',
  'MglCircleLayer',
  'MglFillExtrusionLayer',
  'MglFillLayer',
  'MglHeatmapLayer',
  'MglHillshadeLayer',
  'MglLineLayer',
  'MglRasterLayer',
  'MglSymbolLayer',
]

export const composables = [
  'useMap',
  'useControl',
  'useDisposableLayer',
  'usePositionWatcher',
  'useSource',
]

export default defineNuxtModule<ModuleOptions>({
  meta: {
    name: 'maplibre',
    configKey: 'maplibre',
    compatibility: {
      nuxt: '>=3.0.0',
    },
  },
  defaults: {
    css: 'maplibre-gl/dist/maplibre-gl.css',
  },
  async setup(options, nuxt) {
    if (options.css) {
      if (Array.isArray(options.css)) nuxt.options.css.push(...options.css)
      else nuxt.options.css.push(options.css)
    }

    for (const component of components) {
      addComponent({
        name: component,
        export: component,
        filePath: '@indoorequal/vue-maplibre-gl',
        chunkName: `nuxt-maplibre/${component}`,
        mode: 'all',
      })
    }

    for (const composable of composables) {
      addImports({
        name: composable,
        as: `useMgl${composable.slice(3)}`,
        from: '@indoorequal/vue-maplibre-gl',
      })
    }
  },
})
