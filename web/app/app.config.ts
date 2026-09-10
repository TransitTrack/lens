export default defineAppConfig({
  ui: {
    colors: {
      primary: 'indigo',
      neutral: 'zinc',
    },
  },
  /** Measurement system for speeds and distances shown in the UI. */
  units: 'metric' as 'metric' | 'imperial',
})
