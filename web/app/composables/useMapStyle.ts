const STYLES = {
  dark: 'https://tiles.versatiles.org/assets/styles/shadow/style.json',
  light: 'https://tiles.versatiles.org/assets/styles/graybeard/style.json',
}

/** VersaTiles basemap style URL that tracks the active color mode. */
export function useMapStyle() {
  const colorMode = useColorMode()
  return computed(() => (colorMode.value === 'dark' ? STYLES.dark : STYLES.light))
}
