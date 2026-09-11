export default defineAppConfig({
  ui: {
    colors: {
      primary: 'indigo',
      neutral: 'zinc',
    },
    alert: {
      slots: {
        // `overflow-hidden` on a flex row with a wrapping multi-line
        // description collapses the alert to single-line height in
        // Chromium (the description renders but is clipped/invisible).
        root: 'overflow-visible',
      },
    },
  },
  /** Measurement system for speeds and distances shown in the UI. */
  units: 'metric' as 'metric' | 'imperial',
})
