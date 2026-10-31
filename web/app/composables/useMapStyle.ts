import type { Map as MapLibreMap } from 'maplibre-gl'

const STYLES = {
  dark: 'https://tiles.versatiles.org/assets/styles/natural-dark/style.json',
  light: 'https://tiles.versatiles.org/assets/styles/natural/style.json',
}

/** Hide the basemap's symbol layers after each style load while leaving the
 * application's own route, stop, vehicle, and measurement labels intact. */
export function hideBasemapLabels(map: MapLibreMap | null | undefined) {
  if (!map) return
  for (const layer of map.getStyle().layers ?? []) {
    if (layer.type === 'symbol') map.setLayoutProperty(layer.id, 'visibility', 'none')
  }
}

/** VersaTiles basemap style URL that tracks the active color mode. */
export function useMapStyle() {
  const colorMode = useColorMode()
  return computed(() => (colorMode.value === 'dark' ? STYLES.dark : STYLES.light))
}
